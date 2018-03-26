use std::io::{Result, Write, Read, stdin};
use std::net::TcpStream;
use std::thread;

fn write_u32<W: Write>(num: u32, write: &mut W) -> Result<()> {
    let bytes = (0..4)
        .rev()
        .map(|i| num.wrapping_shr(i * 8) as u8)
        .collect::<Vec<u8>>();
    write.write_all(&bytes)
}

fn write_frame<W: Write>(data: &[u8], write: &mut W) -> Result<()> {
    write_u32(data.len() as u32, write)?;
    write.write_all(data)
}

fn read_u32<R: Read>(read: &mut R) -> Result<u32> {
    let mut buf = [0u8; 4];
    read.read_exact(&mut buf)?;
    Ok(buf
        .into_iter()
        .rev()
        .enumerate()
        .map(|(i, byte)| (*byte as u32).wrapping_shl(i as u32 * 8))
        .fold(0, |x, y| x | y))
}

fn read_frame<R: Read>(read: &mut R) -> Result<Vec<u8>> {
    let mut buf = Vec::new();
    let len = read_u32(read)?;
    buf.resize(len as usize, 0);
    read.read_exact(&mut buf)?;
    Ok(buf)
}

fn read_frames<R: Read>(read: &mut R) -> Result<()> {
    loop {
        let frame = read_frame(read)?;
        println!("{:?}", frame);
    }
}

fn write_frames<W: Write>(write: &mut W) -> Result<()> {
    let mut buf = String::new();
    loop {
        stdin().read_line(&mut buf)?;
        write_frame(&buf.bytes().collect::<Vec<u8>>(), write)?;
        buf.clear();
    }
}

fn main() {
    let mut conn = TcpStream::connect("127.0.0.1:8081").unwrap();
    let mut reader_conn = conn.try_clone().unwrap();
    thread::spawn(move || {
        read_frames(&mut reader_conn).unwrap();
    });

    let data = [1u8, 2, 3, 4];
    write_frame(&data, &mut conn).unwrap();

    write_frames(&mut conn).unwrap();
}
