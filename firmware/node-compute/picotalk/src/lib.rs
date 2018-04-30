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



pub enum TransmitState {
	HandshakeAdvertise(u8),
	HandshakeListen(u8),
	HandshakeWaitRetry(u8),
	HandshakeCheckRx,
	HandshakeWaitTx(u8),
	Preamble(u8),
	SendData(u8),
}

enum RecieveState {
	First(i8),
	Sec(i8),
	Third(i8),
}


/*Statemachine for handshake/recieving/transmitting value*/
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
			if *n < 8 {
				let mask = 1 << *n;
				let databit = value & mask;
				if databit == mask {
					OutputPin::set_high(pin);
					*n += 1;
				} else {
					OutputPin::set_low(pin);
					*n += 1;
				}
			}
		},
		_ => panic!("Not a TransmitState"),
	}
}
