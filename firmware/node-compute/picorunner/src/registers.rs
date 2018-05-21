//Holds the register structs, enums and functions.

//#![no_std]

use rtfm_core::{Resource, Threshold};

use embedded_hal::digital::{OutputPin, InputPin};
use embedded_hal::timer::{CountDown, Periodic};
use stm32f0x0_hal::time::Hertz;

use picotalk::PICOTALK_FREQ;

/**************************** Register Structs ****************************/

pub trait IoPinout {
    type Left: OutputPin + InputPin;
    type Right: OutputPin + InputPin;
    type Up: OutputPin + InputPin;
    type Down: OutputPin + InputPin;
    type TimerResource: Resource<Data=Self::Timer>;
    type Timer: CountDown<Time=Self::TimerUnit> + Periodic + Send;
    type TimerUnit: From<Hertz>;
}

//The struct will hold the value for the memory register
pub struct Interpreter<'a, P: 'a + IoPinout> {
    pub reg_acc: i8,
    pub prog_counter: usize,
    pub flag: Flag,
    pub left_pin: &'a mut P::Left,
    pub right_pin: &'a mut P::Right,
    pub up_pin: &'a mut P::Up,
    pub down_pin: &'a mut P::Down,
    pub timer: P::TimerResource,
}

impl<'a, P: IoPinout> Interpreter<'a, P> {
    pub fn new(down_pin: &'a mut P::Down, left_pin: &'a mut P::Left, up_pin: &'a mut P::Up, right_pin: &'a mut P::Right, timer: P::TimerResource) -> Interpreter<'a, P> {
        Interpreter {
            reg_acc: 0,
            prog_counter: 0,
            flag: Flag::Neather,
            up_pin: up_pin,
            down_pin: down_pin,
            left_pin: left_pin,
            right_pin: right_pin,
            timer: timer,
        }
    }
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
    fn read<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold) -> i8;
}

//interface for writing to register
pub trait RegWrite {
    fn write<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold, value: i8);
}

/********************* Implementations of register Traits *********************/

impl Register {
    pub fn from_i8(var: i8) -> Register {
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
    fn write<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold, value: i8) {
        match self {
            Register::Mem(mem) => mem.write(interpreter, t, value), //calls the memory registers write func.
            Register::Io(reg) => reg.write(interpreter, t, value),
        }
    }
}

//implementing the read func for the io registers.
impl RegRead for IoRegister { //Not done! no value returned

    fn read<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold) -> i8 {
        match self {
            IoRegister::Up => {
                read_loop(interpreter.up_pin, &mut interpreter.timer, t)
            },
            IoRegister::Down => {
                read_loop(interpreter.down_pin, &mut interpreter.timer, t)
            },
            IoRegister::Right => {
                read_loop(interpreter.right_pin, &mut interpreter.timer, t)
            },
            IoRegister::Left => {
                read_loop(interpreter.left_pin, &mut interpreter.timer, t)
            },
        }
    }
}

fn read_loop<P: OutputPin + InputPin, T: CountDown + Periodic + Send, TR: Resource<Data=T>>(pin: &mut P, timer: &mut TR, t: &mut Threshold) -> i8 where T::Time: From<Hertz> {
    use picotalk::*;
    let mut state = RecieveState::HandshakeListen(0);
    while InputPin::is_high(pin) {}
    timer.claim_mut(t, |timer, _t| timer.start(PICOTALK_FREQ));
    loop {
        if let Some(value) = timer.claim_mut(t, |timer, _t| loop {
            block!(timer.wait()).unwrap();
            recieve_value(pin, &mut state);
            match state {
                RecieveState::Done(value) => return Some(value),
                RecieveState::HandshakeListen(0) => return None,
                _ => {},
            };
        }) {
            return value;
        };
    }
}

impl RegWrite for IoRegister {
    fn write<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold, value: i8) {
        match self {
            IoRegister::Up => write_loop(interpreter.up_pin, &mut interpreter.timer, t, value),
            IoRegister::Down => write_loop(interpreter.down_pin, &mut interpreter.timer, t, value),
            IoRegister::Right => write_loop(interpreter.right_pin, &mut interpreter.timer, t, value),
            IoRegister::Left => write_loop(interpreter.left_pin, &mut interpreter.timer, t, value),
        }
    }
}

fn write_loop<P: OutputPin + InputPin, T: CountDown + Periodic + Send, TR: Resource<Data=T>>(pin: &mut P, timer: &mut TR, t: &mut Threshold, value: i8) where T::Time: From<Hertz> {
    use picotalk::*;
    let mut state = TransmitState::HandshakeAdvertise(0);
    timer.claim_mut(t, |timer, _t| timer.start(PICOTALK_FREQ));
    loop {
        if let Some(value) = timer.claim_mut(t, |timer, _t| loop {
            block!(timer.wait()).unwrap();
            transmit_value(pin, &mut state, value);
            match state {
                TransmitState::Idle => return Some(()),
                TransmitState::HandshakeAdvertise(0) => return None,
                _ => {},
            };
        }) {
            return value;
        };
    }
}

//implementing the write func for the memory register.
impl RegWrite for MemRegister {
    fn write<P: IoPinout>(self, interpreter: &mut Interpreter<P>, _t: &mut Threshold, value: i8) {
        match self {
            MemRegister::Acc => interpreter.reg_acc = value,     //assagnes the value to the reg_acc in the interpreter.
            MemRegister::Null => {},                             //felmedelande att det inte går att implementera
        }
    }
}

//implementing the read func for the registers.
impl RegRead for Register {
    fn read<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold) -> i8 {
        match self {
            Register::Io(io) => io.read(interpreter, t),            //If self is a io reg then it calls for io regs read func.
            Register::Mem(mem) => mem.read(interpreter, t),         //If self is a mem reg then it calls the mem regs read func.
        }
    }
}

//implementing the read func for the memory registers.
impl RegRead for MemRegister {
    fn read<P: IoPinout>(self, interpreter: &mut Interpreter<P>, _t: &mut Threshold) -> i8 {
        match self {
            MemRegister::Acc => interpreter.reg_acc,         //reading the value in the rag_acc in the interpeter and returning it.
            MemRegister::Null => 0,                          //Reading from the null register.
        }
    }
}

//Implementing read func for the R/I.
impl RegRead for RegisterOrImmediate {
    fn read<P: IoPinout>(self, interpreter: &mut Interpreter<P>, t: &mut Threshold) -> i8 {
        match self {
            RegisterOrImmediate::Reg(reg) => reg.read(interpreter, t), //If self is a io reg then it calls for io regs read func.
            RegisterOrImmediate::Immediate(var) => var,             //If self is a mem reg then it calls the mem regs read func.
        }
    }
}
