#![no_std]

//The struct will hold the value for the memory register
pub struct Interpreter {
    reg_acc: i8,
    prog_counter: u8,
}

impl Interpreter {
    pub fn new() -> Interpreter {
        Interpreter {
            reg_acc: 0,
            prog_counter: 0,
        }
    }
}

//The Picoasm instructions that we can recieve
enum Instruction {
    Mov(RegisterOrImmediate, Register),
    Add,
    Sub,
    Teq,
    Tgt,
    Tlt,
    Tcp,
}

//Enumerate the memory register
#[derive(Clone, Copy)]
pub enum MemRegister {
    Acc,
    Null,
}

//Enumerating the IO registers, one register for each node it can communicate with.
#[derive(Clone, Copy)]
enum IoRegister {
    Right,
    Left,
    Up,
    Down,
}

//Enumerating the different types of registers.
#[derive(Clone, Copy)]
enum Register {
    Mem(MemRegister),
    Io(IoRegister),
}

//interface for reading from register
pub trait RegRead {
    fn read(self, interpreter: &Interpreter) -> i8;
}

//interface for writing to register
pub trait RegWrite {
    fn write(self, interpreter: &mut Interpreter, value: i8);
}

//implementing the write to register function. It takes the input value and assign it to the interpreter.
impl RegWrite for Register {
    fn write(self, interpreter: &mut Interpreter, value: i8) {
        match self {
            Register::Mem(mem) => mem.write(interpreter, value), //calls the memory registers write func.
            Register::Io(_) => unimplemented!(),
        }
    }
}

//implementing the write func for the memory register.
impl RegWrite for MemRegister {
    fn write(self, interpreter: &mut Interpreter, value: i8) {
        match self {
            MemRegister::Acc => interpreter.reg_acc = value, //assagnes the vaue to the reg_acc in the interpreter.
            MemRegister::Null => {} //felmedelande att det inte går att implementera
        }
    }
}

//implementing the read func for the registers.
impl RegRead for Register {
    fn read(self, interpreter: &Interpreter) -> i8 {
        match self {
            Register::Io(io) => io.read(interpreter), //If self is a io reg then it calls for io regs read func.
            Register::Mem(mem) => mem.read(interpreter), //If self is a mem reg then it calls the mem regs read func.
        }
    }
}

//implementing the read func for the memory registers.
impl RegRead for MemRegister {
    fn read(self, interpreter: &Interpreter) -> i8 {
        match self {
            MemRegister::Acc => interpreter.reg_acc, //reading the value in the rag_acc in the interpeter and returning it.
            MemRegister::Null => 0, //Reading from the null register. It is always 0.
        }
    }
}

//implementing the read func for the io registers.
impl RegRead for IoRegister {
    fn read(self, interpeter: &Interpreter) -> i8 {
        unimplemented!()
    }
}

//Decoding the Bytecode in the format flag(2 bits), op(6 bits), operand A(8 bits), operand B(8 bits)
struct Bytecode{
	bytecode: [u8,3];
}
	
fn decoding_instruction(/*bytecode Bytecode*/) -> Instruction {
	match bytecode[0] {
	[0, op_a, op_b] => Mov(RegisterOrImmediate::from_u8(op_a), Register::from_u8(op_b))
	[1, op_a, 0] => add
	[1, op_a, 1] => sub
		u8::0 => mov,
		u8::1 => add,
		u8::2 => sub,
		u8::3 => teq,
		u8::4 => tgt,
		u8::5 => tlt,
		u8::6 => tcp,
	}
}


#[cfg(test)]
mod tests {
	use ::*;

    #[test]
    fn acc_roundtrip() {
        let mut interp = Interpreter::new();
		let reg = MemRegister::Acc;
		reg.write(&mut interp, 5);
		assert_eq!(reg.read(&interp), 5);
    }
}
