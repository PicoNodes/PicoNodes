//Holds the register structs, enums and functions.

#![no_std]





/**************************** Register Structs ****************************/

//The struct will hold the value for the memory register
pub struct Interpreter {
    pub reg_acc: i8,
    pub prog_counter: usize,
    pub flag: Flag,
}

impl Interpreter {
    pub fn new() -> Interpreter {
        Interpreter {
            reg_acc: 0,
            prog_counter: 0,
            flag: Flag::Neather,
        }
    }
}

impl Interpreter {
    pub fn set_flag(&mut self, state: bool) {
        if state == true {
            self.flag = Flag::True;
        } else if state == false {
            self.flag = Flag::False;
        } else {
            self.flag = Flag::Neather;
        }
    }
}

/***************************** Register Enums *****************************/

//different types of registers.
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd)]
pub enum Register {
    Mem(MemRegister),
    Io(IoRegister),
}

//memory register
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd)]
pub enum MemRegister {
    Acc,
    Null,
}

//IO registers, one register for each node it can communicate with.
#[derive(Clone, Copy, PartialEq, Eq, PartialOrd)]
pub enum IoRegister {
    Right,
    Left,
    Up,
    Down,
}

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd)] //Look into if the derive is redundant
pub enum RegisterOrImmediate {
    Reg(Register),
    Immediate(i8),
}

#[derive(Clone, Copy, PartialEq, Eq)] //Adding traits
pub enum Flag {
    True,
    False,
    Neather,
}

/****************************** Register Traits ******************************/

//interface for reading from register
pub trait RegRead {
    fn read(self, interpreter: &Interpreter) -> i8;
}

//interface for writing to register
pub trait RegWrite {
    fn write(self, interpreter: &mut Interpreter, value: i8);
}

/********************* Implementations of register Traits *********************/

impl Register {
    pub fn from_i8(var: i8) -> Register {
        use self::IoRegister::*;
        use Register::*;
        match var {
            -128 => Io(IoRegister::Up),
            -127 => Io(IoRegister::Down),
            -126 => Io(IoRegister::Left),
            -125 => Io(IoRegister::Right),
            126 => Mem(MemRegister::Acc),
            127 => Mem(MemRegister::Null),
            _ => panic!("Register {} is reserved", var),
        }
    }
}
impl RegisterOrImmediate {
    pub fn from_i8(var: i8) -> RegisterOrImmediate {
        use self::RegisterOrImmediate::*;
        if var >= -100 && var <= 100 {
            Immediate(var)
        } else {
            Reg(Register::from_i8(var))
        }
    }
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

//implementing the read func for the io registers.
impl RegRead for IoRegister {
    fn read(self, interpreter: &Interpreter) -> i8 {
        unimplemented!()
    }
}

//implementing the write func for the memory register.
impl RegWrite for MemRegister {
    fn write(self, interpreter: &mut Interpreter, value: i8) {
        match self {
            MemRegister::Acc => interpreter.reg_acc = value, //assagnes the value to the reg_acc in the interpreter.
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
            MemRegister::Null => 0,                  //Reading from the null register.
        }
    }
}

//Implementing read func for the R/I.
impl RegRead for RegisterOrImmediate {
    fn read(self, interpreter: &Interpreter) -> i8 {
        match self {
            RegisterOrImmediate::Reg(reg) => reg.read(interpreter), //If self is a io reg then it calls for io regs read func.
            RegisterOrImmediate::Immediate(var) => var, //If self is a mem reg then it calls the mem regs read func.
        }
    }
}
