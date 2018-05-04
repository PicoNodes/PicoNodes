/*File for communicating between PicoNodes*/
/*Author Therese Kennerberg <tke@kth.se> and Teo Klestrup Röijezon <teo@nullable.se>*/

#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_semihosting;

extern crate stm32f0x0_hal;
extern crate embedded_hal;

use core::fmt::Write;
use embedded_hal::digital::*;
use stm32f0x0_hal::gpio::*;
use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::delay::Delay;


#[derive(Debug)]
pub enum TransmitState {
	  HandshakeAdvertise(u8),
	  HandshakeListen(u8),
	  HandshakeWaitRetry(u8),
	  HandshakeCheckRx,
	  HandshakeWaitTx(u8),
	  Preamble(u8),
	  SendData(u8),
    Idle,
}

#[derive(Debug)]
pub enum RecieveState {
	  HandshakeListen(u8),
	  HandshakeConfirm,
	  HandshakeWaitRx(u8),
	  ReadPreamble(u8),
	  RecieveData(u8, i8),
	  Done(i8),
	  Idle,
}

/*Statemachine for handshake/recievedata*/
pub fn recieve_value<P: OutputPin + InputPin>(pin: &mut P, state: &mut RecieveState) {
	  use RecieveState::*;

	  match state {
		    HandshakeListen(0) => {
			      if InputPin::is_low(pin) {
				        *state = HandshakeListen(1);
			      }
		    },
		    HandshakeListen(1) => {
			      if InputPin::is_low(pin) {
				        *state = HandshakeConfirm;
			      } else {
				        *state = HandshakeListen(0);
			      }
		    },
		    HandshakeConfirm => {
			      OutputPin::set_low(pin);
			      *state = HandshakeWaitRx(0);
		    },
		    HandshakeWaitRx(0) => {
			      OutputPin::set_high(pin);
			      *state = HandshakeWaitRx(1);
		    },
		    HandshakeWaitRx(1) => {
			      if InputPin::is_high(pin) {
				        *state = ReadPreamble(0);
			      } else {
				        panic!("Expecting high pin for recieving!")
			      }
		    },
		    ReadPreamble(0) => {
			      if InputPin::is_high(pin) {
				        *state = ReadPreamble(1);
			      } else {
				        panic!("Dont recieve the first preamble!")
			      }
		    },
		    ReadPreamble(1) => {
			      if InputPin::is_low(pin) {
				        *state = ReadPreamble(2);
			      } else {
				        panic!("Dont recieve the second preamble!");
			      }
		    },
		    ReadPreamble(2) => {
			      if InputPin::is_high(pin) {
				        *state = ReadPreamble(3);
			      } else {
				        panic!("Dont recieve the third preamble!")
			      }
		    },
		    ReadPreamble(3) => {
			      if InputPin::is_low(pin) {
				        *state = ReadPreamble(4);
			      } else {
				        panic!("Dont recieve the fourth preamble!");
			      }
		    },
		    ReadPreamble(4) => {
			      if InputPin::is_high(pin) {
				        *state = RecieveData(0, 0);
			      } else {
				        panic!("Dont recieve the fourth preamble!");
			      }
		    },
		    RecieveData(ref mut n, ref mut value) => {
			      let mask = 1 << *n;

			      if *n < 7 {
				        if InputPin::is_high(pin) {
					          *value = *value | mask;
				        }
			      } else if *n == 7 {
				        *state = Done(*value);
			      } else {
				        panic!("Data recieved is to long");
			      }
			      *n += 1;
		    },
		    Done(_) => {
		    },
		    Idle => {
		    },
		    state => panic!("Not a RecieveState: {:?}!", state),
 	  }
}

/*Statemachine for handshake/transmitting value*/
pub fn transmit_value<P: OutputPin + InputPin>(pin: &mut P, state: &mut TransmitState, value: i8) {
	  use TransmitState::*;
	  match state {
		    HandshakeAdvertise(0) => {
			      *state = HandshakeAdvertise(1);
			      OutputPin::set_low(pin);
		    },
		    HandshakeAdvertise(1) => {
			      *state = HandshakeListen(0);
		    },
		    HandshakeListen(ref mut n) => {
			      OutputPin::set_high(pin);    				//reseting the pin
			      if InputPin::is_low(pin) {
				        *state = HandshakeCheckRx;
			      } else if *n < 2 {
				        *n += 1;
			      } else {
				        *state = HandshakeWaitRetry(0);
			      }
		    },
		    HandshakeWaitRetry(0) => {
			      *state = HandshakeWaitRetry(1);
		    },
		    HandshakeWaitRetry(1) => {
			      *state = HandshakeAdvertise(0);
		    },
		    HandshakeCheckRx => {
			      if InputPin::is_low(pin) {
				        panic!("Transmission from both sides!");
			      } else {
				        *state = HandshakeWaitTx(0);
			      }
		    },
		    HandshakeWaitTx(0) => {
			      *state = HandshakeWaitTx(1);
		    },
		    HandshakeWaitTx(1) => {
			      *state = Preamble(0);
		    },
		    Preamble(0) => {
			      OutputPin::set_high(pin);
			      *state = Preamble(1);
		    },
		    Preamble(1) => {
			      OutputPin::set_low(pin);
			      *state = Preamble(2);
		    },
		    Preamble(2) => {
			      OutputPin::set_high(pin);
			      *state = Preamble(3);
		    },
		    Preamble(3) => {
			      OutputPin::set_low(pin);
			      *state = Preamble(4);
		    },
		    Preamble(4) => {
			      OutputPin::set_high(pin);
			      *state = SendData(0);
		    },
		    SendData(ref mut n) => {
				    let mask = 1 << *n;
				    let databit = value & mask;
				    if databit == mask {
					      OutputPin::set_high(pin);
				    } else {
					      OutputPin::set_low(pin);
				    }
			      if *n < 7 {
					      *n += 1;
            } else {
                *state = Idle;
            }
		    },
        Idle => {
            OutputPin::set_high(pin);
        },
		    _ => panic!("Not a TransmitState"),
	  }
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_write_pin() {
        assert_eq!(2 + 2, 4);
    }
}
