#![no_std]

mod registers;
use registers::*;








//The Picoasm instructions structure
enum Instruction {
    Mov(RegisterOrImmediate, Register), //Mov(RegisterOrImmediate, Register),(RegisterOrImmediate, Register)
    Add(i8), //Need to be Add(RegisterOrImmediate) to be able to get a value from both register and immediate
    Sub(i8), //Need to be Sub(RegisterOrImmediate) to be able to get a value from both register and immediate or it can be decoded in the actions??
    Teq(RegisterOrImmediate, RegisterOrImmediate),
    Tgt(RegisterOrImmediate, RegisterOrImmediate),
    Tlt(RegisterOrImmediate, RegisterOrImmediate),
    Tcp(RegisterOrImmediate, RegisterOrImmediate),
}
	

//Decoding the Bytecode in the format flag(2 bits), op(6 bits), operand A(8 bits), operand B(8 bits)
struct Bytecode{
	bytecode: [u8;3],
}


fn decoding_instruction(bytecode: Bytecode) -> Instruction{
	use Instruction::*;
	let flags = (bytecode.bytecode[0] & 0xC0) >> 6;
	let op = bytecode.bytecode[0] & 0x3F;
	let op_a = bytecode.bytecode[1] as i8;
	let op_b = bytecode.bytecode[2] as i8;
		
	match op {
		0 => Mov(registers::RegisterOrImmediate::from_i8(op_a), registers::Register::from_i8(op_b)),
		1 if op_b == 0 => Add(op_a), //Under construction! Add must also be able to get a value from a register
		1 if op_b == 1 => Sub(op_a), //Same for Sub!
		4 => Teq(registers::RegisterOrImmediate::from_i8(op_a), registers::RegisterOrImmediate::from_i8(op_b)),
		5 => Tgt(registers::RegisterOrImmediate::from_i8(op_a), registers::RegisterOrImmediate::from_i8(op_b)),
		6 => Tlt(registers::RegisterOrImmediate::from_i8(op_a), registers::RegisterOrImmediate::from_i8(op_b)),
		7 => Tcp(registers::RegisterOrImmediate::from_i8(op_a), registers::RegisterOrImmediate::from_i8(op_b)),
		_ => panic!("Not an instruction!"),
	}
}

//The func takes the decoded instruction and do actions depending on the operation	
fn run_instruction(instruction: Instruction, interpreter: &mut Interpreter) {
	use Instruction::*;
	match instruction {
		Mov(op_a, op_b) => {
			let value = op_a;
		}
		Add(op_a) => {
			interpreter.reg_acc += op_a;
		},
		Sub(op_a) => {
			interpreter.reg_acc -= op_a;
		},
		_ => 
			unimplemented!(),
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
