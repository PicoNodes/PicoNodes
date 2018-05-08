#![no_std]

mod registers;
pub use registers::*;

pub const INSTRUCTION_BYTES: u8 = 3;

//The Picoasm instructions structure
pub enum Instruction {
    Mov(Flag, RegisterOrImmediate, Register), //Mov(RegisterOrImmediate, Register),(RegisterOrImmediate, Register)
    Add(Flag, RegisterOrImmediate), //Need to be Add(RegisterOrImmediate) to be able to get a value from both register and immediate
    Sub(Flag, RegisterOrImmediate), //Need to be Sub(RegisterOrImmediate) to be able to get a value from both register and immediate or it can be decoded in the actions??
    Teq(Flag, RegisterOrImmediate, RegisterOrImmediate),
    Tgt(Flag, RegisterOrImmediate, RegisterOrImmediate),
    Tlt(Flag, RegisterOrImmediate, RegisterOrImmediate),
    Tcp(Flag, RegisterOrImmediate, RegisterOrImmediate),
}

impl Instruction {
    fn get_flag(&self) -> Flag {
        use registers::Flag::*;
        use Instruction::*;
        match self {
            Mov(flag, _, _) => *flag,
            Add(flag, _) => *flag,
            Sub(flag, _) => *flag,
            Teq(flag, _, _) => *flag,
            Tgt(flag, _, _) => *flag,
            Tlt(flag, _, _) => *flag,
            Tcp(flag, _, _) => *flag,
        }
    }
}

//Decoding the Bytecode in the format flag(2 bits), op(6 bits), operand A(8 bits), operand B(8 bits)
pub fn decode_instruction(bytecode: &[u8]) -> Instruction {
    use registers::Flag::*;
    use Instruction::*;
    let flags = (bytecode[0] & 0xC0) >> 6;
    let op = bytecode[0] & 0x3F;
    let op_a = bytecode[1] as i8;
    let op_b = bytecode[2] as i8;
    let mut flag = Flag::Neather;

    //Checking the intruction flags exclusions
    match flags {
        2 => flag = True,
        1 => flag = False,
        _ => flag = Neather,
    };

    match op {
        0 => Mov(
            flag,
            registers::RegisterOrImmediate::from_i8(op_a),
            registers::Register::from_i8(op_b),
        ),
        1 if op_b == 0 => Add(flag, registers::RegisterOrImmediate::from_i8(op_a)), //Under construction! Add must also be able to get a value from a register
        1 if op_b == 1 => Sub(flag, registers::RegisterOrImmediate::from_i8(op_a)), //Same for Sub!
        4 => Teq(
            flag,
            registers::RegisterOrImmediate::from_i8(op_a),
            registers::RegisterOrImmediate::from_i8(op_b),
        ),
        5 => Tgt(
            flag,
            registers::RegisterOrImmediate::from_i8(op_a),
            registers::RegisterOrImmediate::from_i8(op_b),
        ),
        6 => Tlt(
            flag,
            registers::RegisterOrImmediate::from_i8(op_a),
            registers::RegisterOrImmediate::from_i8(op_b),
        ),
        7 => Tcp(
            flag,
            registers::RegisterOrImmediate::from_i8(op_a),
            registers::RegisterOrImmediate::from_i8(op_b),
        ),
        _ => panic!("Not an instruction!"),
    }
}

//The func takes the decoded instruction and do actions depending on the operation
pub fn run_instruction(instruction: Instruction, interpreter: &mut Interpreter) {
    use Instruction::*;
    let flag = instruction.get_flag();
    if flag == interpreter.flag {
        match instruction {
            Mov(_, op_a, op_b) => {
                let value = op_a.read(interpreter);
                op_b.write(interpreter, value); //write structure is write(self, interpeter, value) let value = op_a;
            }
            Add(_, op_a) => {
                //intruction structure is Instruction::Add(RegisterOrImmediate)
                let value = interpreter.reg_acc;
                interpreter.reg_acc = value + op_a.read(interpreter);
            }
            Sub(_, op_a) => {
                let value = interpreter.reg_acc;
                interpreter.reg_acc = value - op_a.read(interpreter);
            }
            Teq(_, op_a, op_b) => {
                let state = (op_a.read(interpreter) == op_b.read(interpreter));
                interpreter.set_flag(state);
            }
            Tgt(_, op_a, op_b) => {
                let state = (op_a.read(interpreter) > op_b.read(interpreter));
                interpreter.set_flag(state);
            }
            Tlt(_, op_a, op_b) => {
                let state = (op_a.read(interpreter) < op_b.read(interpreter));
                interpreter.set_flag(state);
            }
            Tcp(_, op_a, op_b) => {
                if op_a == op_b {
                    interpreter.flag = Flag::Neather;
                } else if op_a < op_b {
                    interpreter.flag = Flag::False;
                } else {
                    interpreter.flag = Flag::True;
                }
            }
            _ => unimplemented!(),
        }
    }
    interpreter.prog_counter += INSTRUCTION_BYTES;
}

#[cfg(test)]
mod tests {
    use *;

    #[test]
    fn acc_roundtrip() {
        let mut interp = Interpreter::new();
        let reg = MemRegister::Acc;
        reg.write(&mut interp, 6);
        assert_eq!(reg.read(&interp), 6);
    }

    #[test]
    fn test_instructions() {
        let reg_a = RegisterOrImmediate::Reg(Register::Io(IoRegister::Up));
        let reg_b = RegisterOrImmediate::Reg(Register::Mem(MemRegister::Acc));
        let reg_c = Register::Mem(MemRegister::Acc);

        let instr_mov = Instruction::Mov(Flag::Neather, RegisterOrImmediate::Immediate(3), reg_c);
        let instr_add_1 = Instruction::Add(Flag::False, RegisterOrImmediate::Immediate(4));
        let instr_add_2 = Instruction::Add(Flag::True, RegisterOrImmediate::Immediate(34));
        let instr_sub = Instruction::Sub(Flag::True, RegisterOrImmediate::Immediate(37));
        let instr_teq = Instruction::Teq(Flag::Neather, RegisterOrImmediate::Immediate(6), reg_b);
        let instr_tgt = Instruction::Tgt(Flag::False, RegisterOrImmediate::Immediate(9), reg_b);
        let instr_tlt = Instruction::Tlt(Flag::True, RegisterOrImmediate::Immediate(11), reg_a);
        let instr_tcp = Instruction::Tcp(Flag::True, RegisterOrImmediate::Immediate(34), reg_a);
        let mut interp = Interpreter::new();

        run_instruction(instr_mov, &mut interp);
        assert_eq!(reg_b.read(&interp), 3);

        run_instruction(instr_teq, &mut interp);
        run_instruction(instr_add_1, &mut interp);
        assert_eq!(reg_b.read(&interp), 7);

        run_instruction(instr_tgt, &mut interp);
        run_instruction(instr_sub, &mut interp);
        assert_eq!(reg_b.read(&interp), -30);
    }
}
