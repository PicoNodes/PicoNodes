#![no_std]

#[cfg(test)]
#[macro_use]
extern crate std;

extern crate heapless;
extern crate embedded_hal;
#[macro_use]
extern crate nb;
extern crate byteorder;

use core::convert::From;

use heapless::{Vec, BufferFullError};
use embedded_hal::serial;
use byteorder::{BigEndian, ByteOrder};

#[derive(Debug)]
pub enum DecodeError {
    InvalidType,
    TooLargeMessage,
}

impl From<BufferFullError> for DecodeError {
    fn from(_err: BufferFullError) -> DecodeError {
        DecodeError::TooLargeMessage
    }
}

#[derive(Debug)]
pub enum ReadError<R: serial::Read<u8>> {
    Serial(R::Error),
    Decode(DecodeError),
}

impl<R: serial::Read<u8>> From<DecodeError> for ReadError<R> {
    fn from(err: DecodeError) -> ReadError<R> {
        ReadError::Decode(err)
    }
}

struct RawMessage {
    bytes: Vec<u8, [u8; 64]>
}

impl RawMessage {
    fn read<R: serial::Read<u8>>(reader: &mut R) -> Result<RawMessage, ReadError<R>> {
        let length = RawMessage::read_u32(reader).map_err(|x| ReadError::Serial(x))?;
        let mut bytes = Vec::new();
        bytes.resize(length as usize, 0).unwrap();
        RawMessage::read_into_slice(reader, &mut bytes).map_err(|x| ReadError::Serial(x))?;
        Ok(RawMessage {
            bytes
        })
    }

    fn read_into_slice<R: serial::Read<u8>>(reader: &mut R, slice: &mut [u8]) -> Result<(), R::Error> {
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
}

#[derive(Debug, PartialEq, Eq)]
pub enum Command {
    DownloadBytecode(Vec<u8, [u8; 60]>),
}

impl Command {
    fn from_raw(raw: RawMessage) -> Result<Command, DecodeError> {
        let bytes = raw.bytes;
        let tpe = BigEndian::read_u32(&bytes[0..4]);
        match tpe {
            1 => {
                let mut content = Vec::new();
                content.extend_from_slice(&bytes[4..])?;
                Ok(Command::DownloadBytecode(content))
            },
            _ => Err(DecodeError::InvalidType),
        }
    }

    pub fn read<R: serial::Read<u8>>(reader: &mut R) -> Result<Command, ReadError<R>> {
        let raw = RawMessage::read(reader)?;
        Command::from_raw(raw).map_err(|x| ReadError::Decode(x))
    }
}

#[cfg(test)]
mod tests {
    use heapless::Vec;
    use embedded_hal::serial;
    use nb;

    use std::collections::VecDeque;

    #[derive(Debug)]
    struct BufSerial {
        buf: VecDeque<u8>,
    }
    impl BufSerial {
        fn from_slice(slice: &[u8]) -> BufSerial {
            let mut buf = VecDeque::new();
            for i in slice {
                buf.push_back(*i);
            }
            BufSerial {
                buf
            }
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
            0, 0, 0, 9, // Length
            0, 0, 0, 1, // Type: 1 (DownloadBytecode)
            1, 2, 3, 4, 5,
        ];
        let mut serial = BufSerial::from_slice(&cmd);
        let decoded = ::Command::read(&mut serial).unwrap();

        let mut instr_buf = Vec::new();
        instr_buf.extend_from_slice(&[1, 2, 3, 4, 5]).unwrap();
        assert_eq!(::Command::DownloadBytecode(instr_buf), decoded);
    }
}
