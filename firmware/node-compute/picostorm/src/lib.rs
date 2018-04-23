#![no_std]

#[cfg(test)]
#[macro_use]
extern crate std;

extern crate embedded_hal;
extern crate heapless;
#[macro_use]
extern crate nb;
extern crate byteorder;

use core::convert::From;
use core::fmt::{self, Debug};

use byteorder::{BigEndian, ByteOrder};
use embedded_hal::serial;
use heapless::{BufferFullError, Vec};

#[derive(Debug)]
pub enum DecodeError {
    InvalidType(u32),
    TooLargeMessage,
}

impl From<BufferFullError> for DecodeError {
    fn from(_err: BufferFullError) -> DecodeError {
        DecodeError::TooLargeMessage
    }
}

#[derive(Debug)]
pub enum EncodeError {
    TooLargeMessage,
}

impl From<BufferFullError> for EncodeError {
    fn from(_err: BufferFullError) -> EncodeError {
        EncodeError::TooLargeMessage
    }
}

pub enum ReadError<R: serial::Read<u8>> {
    Serial(R::Error),
    Decode(DecodeError),
}

impl<R: serial::Read<u8>> Debug for ReadError<R>
where
    R::Error: Debug,
{
    fn fmt(&self, fmt: &mut fmt::Formatter) -> fmt::Result {
        match (&*self,) {
            (&ReadError::Serial(ref inner),) => {
                let mut dbg_builder = fmt.debug_tuple("Serial");
                let _ = dbg_builder.field(inner);
                dbg_builder.finish()
            }
            (&ReadError::Decode(ref inner),) => {
                let mut dbg_builder = fmt.debug_tuple("Decode");
                let _ = dbg_builder.field(inner);
                dbg_builder.finish()
            }
        }
    }
}

impl<R: serial::Read<u8>> From<DecodeError> for ReadError<R> {
    fn from(err: DecodeError) -> ReadError<R> {
        ReadError::Decode(err)
    }
}

#[derive(Debug)]
pub enum WriteError<W: serial::Write<u8>> {
    Serial(W::Error),
    Encode(EncodeError),
}

impl<W: serial::Write<u8>> From<EncodeError> for WriteError<W> {
    fn from(err: EncodeError) -> WriteError<W> {
        WriteError::Encode(err)
    }
}

struct RawMessage {
    bytes: Vec<u8, [u8; 64]>,
}

impl RawMessage {
    fn read<R: serial::Read<u8>>(reader: &mut R) -> Result<RawMessage, ReadError<R>> {
        let length = RawMessage::read_u32(reader).map_err(ReadError::Serial)?;
        let mut bytes = Vec::new();
        bytes.resize(length as usize, 0).unwrap();
        RawMessage::read_into_slice(reader, &mut bytes).map_err(ReadError::Serial)?;
        Ok(RawMessage { bytes })
    }

    fn read_into_slice<R: serial::Read<u8>>(
        reader: &mut R,
        slice: &mut [u8],
    ) -> Result<(), R::Error> {
        for value in slice {
            *value = block!(reader.read())?;
        }
        Ok(())
    }

    fn read_u32<R: serial::Read<u8>>(reader: &mut R) -> Result<u32, R::Error> {
        let mut buf = [0; 4];
        RawMessage::read_into_slice(reader, &mut buf)?;
        Ok(BigEndian::read_u32(&buf))
    }

    fn write<W: serial::Write<u8>>(self, writer: &mut W) -> Result<(), WriteError<W>> {
        RawMessage::write_u32(writer, self.bytes.len() as u32).map_err(WriteError::Serial)?;
        RawMessage::write_from_slice(writer, &self.bytes).map_err(WriteError::Serial)
    }

    fn write_from_slice<W: serial::Write<u8>>(
        writer: &mut W,
        slice: &[u8],
    ) -> Result<(), W::Error> {
        for value in slice {
            block!(writer.write(*value))?;
        }
        Ok(())
    }

    fn write_u32<W: serial::Write<u8>>(writer: &mut W, value: u32) -> Result<(), W::Error> {
        let mut buf = [0; 4];
        BigEndian::write_u32(&mut buf, value);
        RawMessage::write_from_slice(writer, &buf)
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum Command {
    Ping,
    DownloadBytecode { bytecode: Vec<u8, [u8; 60]> },
}

impl Command {
    fn from_raw(raw: RawMessage) -> Result<Command, DecodeError> {
        let bytes = raw.bytes;
        if bytes.len() > 0 {
            let tpe = BigEndian::read_u32(&bytes[0..4]);
            match tpe {
                1 => {
                    let mut content = Vec::new();
                    content.extend_from_slice(&bytes[4..])?;
                    Ok(Command::DownloadBytecode { bytecode: content })
                }
                _ => Err(DecodeError::InvalidType(tpe)),
            }
        } else {
            Ok(Command::Ping)
        }
    }

    pub fn read<R: serial::Read<u8>>(reader: &mut R) -> Result<Command, ReadError<R>> {
        let raw = RawMessage::read(reader)?;
        Command::from_raw(raw).map_err(ReadError::Decode)
    }
}

pub enum Event {
    DownloadedBytecode { checksum: u8 },
}

impl Event {
    fn into_raw(self) -> Result<RawMessage, EncodeError> {
        let mut content = Vec::new();
        match self {
            Event::DownloadedBytecode { checksum } => content.extend_from_slice(&[
                0,
                0,
                0,
                1, // Type
                checksum,
            ]),
        }?;
        Ok(RawMessage { bytes: content })
    }

    pub fn write<W: serial::Write<u8>>(self, writer: &mut W) -> Result<(), WriteError<W>> {
        let raw = self.into_raw()?;
        raw.write(writer)
    }
}

#[cfg(test)]
mod tests {
    use embedded_hal::serial;
    use heapless::Vec;
    use nb;

    use std::collections::VecDeque;

    #[derive(Debug)]
    struct BufSerial {
        buf: VecDeque<u8>,
    }
    impl BufSerial {
        fn new() -> BufSerial {
            BufSerial::from_slice(&[])
        }

        fn from_slice(slice: &[u8]) -> BufSerial {
            let mut buf = VecDeque::new();
            for i in slice {
                buf.push_back(*i);
            }
            BufSerial { buf }
        }
    }

    #[derive(Debug)]
    struct Empty;

    impl serial::Read<u8> for BufSerial {
        type Error = Empty;

        fn read(&mut self) -> nb::Result<u8, Empty> {
            self.buf.pop_front().ok_or(nb::Error::Other(Empty))
        }
    }

    impl serial::Write<u8> for BufSerial {
        type Error = !;

        fn write(&mut self, byte: u8) -> nb::Result<(), !> {
            self.buf.push_back(byte);
            Ok(())
        }

        fn flush(&mut self) -> nb::Result<(), !> {
            Ok(())
        }
    }

    #[test]
    fn read_download_bytecode_command() {
        let cmd = [
            0,
            0,
            0,
            9, // Length
            0,
            0,
            0,
            1, // Type: 1 (DownloadBytecode)
            1,
            2,
            3,
            4,
            5,
        ];
        let mut serial = BufSerial::from_slice(&cmd);
        let decoded = ::Command::read(&mut serial).unwrap();

        let mut instr_buf = Vec::new();
        instr_buf.extend_from_slice(&[1, 2, 3, 4, 5]).unwrap();
        assert_eq!(
            ::Command::DownloadBytecode {
                bytecode: instr_buf
            },
            decoded
        );
    }

    #[test]
    fn write_downloaded_bytecode_event() {
        let evt = ::Event::DownloadedBytecode { checksum: 42 };
        let mut serial = BufSerial::new();
        evt.write(&mut serial).unwrap();

        let expected = [
            0,
            0,
            0,
            5, // Length
            0,
            0,
            0,
            1, // Type: 1 (DownloadedBytecode)
            42,
        ];

        assert_eq!(serial.buf, expected);
    }
}
