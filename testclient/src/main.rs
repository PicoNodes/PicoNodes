use std::io::{Result, Write, Read};
use std::net::TcpStream;

fn write_u32<W: Write>(num: u32, write: &mut W) -> Result<()> {
    let bytes = (0..4)
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

fn main() {
    let mut conn = TcpStream::connect("127.0.0.1:8081").unwrap();
    let data: [u8; 4] = [1, 2, 3, 4];
    write_frame(&data, &mut conn).unwrap();
    // let mut response = [0u8; 8];
    // conn.read_exact(&mut response).unwrap();
    let response = read_frame(&mut conn).unwrap();
    println!("{:?}", response);
}
