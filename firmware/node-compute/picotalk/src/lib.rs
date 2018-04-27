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



enum TransmitState {
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
pub fn transmitting_value<P: OutputPin + InputPin>(pin: &mut P, state: &mut TransmitState, value: i8) {
	use TransmitState::*;
	let mut mask = 1;  //let &mut mask = 1;
	match state {
		HandshakeAdvertise(0) => {
			*state = HandshakeAdvertise(1);
			OutputPin::set_low(pin);
		},
		HandshakeAdvertise(1) => {
			OutputPin::set_high(pin);    				//reseting the pin
			*state = HandshakeListen(0);
		},
		HandshakeListen(ref mut n) => {
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
		Preamble(ref mut n) => {
			if *n < 5 {
				OutputPin::set_low(pin);
				*n += 1;
			} else {
				OutputPin::set_high(pin);
				*state = SendData(0);
			}
		},
		SendData(ref mut n) => {
			if *n < 8 {
				let databit = value & mask;
				mask <<= 1;
				if databit == 1 {
					OutputPin::set_low(pin);
					*n += 1;
				} else {
					OutputPin::set_high(pin);
					*n += 1;
				}
			} 
		}
	}
}
//Take an OutputPin, value and the clock as argument and sends the value on the given pin bitwise of a i8.
pub fn write_pin<P: OutputPin>(pin: &mut P, value: i8, delay: &mut Delay) {

	//Can only send i8 values between 100..-100
    let mut state = true;
	let mut n = 1;

	//Preamble is 5 delays high pin so the reciever know when the message start
	for n in 0..5 {
		pin.set_high();
		delay.delay_ms(5*500u16);
		pin.set_low();
	}

	for i in 0..8 {
		if n & value == 0 {
			pin.set_low();
		}else {
			pin.set_high();
		};

		delay.delay_ms(500u16);
		n = n*2;
	};
}

//Transmitting value to a pin. Set the pin one high then wait for one high response then send the message.
/*pub fn transmitting_value<P: OutputPin + InputPin>(pin: &mut P, value: i8, delay: &mut Delay) {
	pin.set_low();
	delay.delay_ms(500u16);
	pin.set_high();

	while InputPin::is_high(pin) {};
	delay.delay_ms(500u16);

	write_pin(pin, value, delay);
}
pub fn recieving_value<P: OutputPin + InputPin>(pin: &mut P, delay: &mut Delay) -> i8{
	let state = false;
	while InputPin::is_high(pin) {};

	pin.set_high();
	delay.delay_ms(500u16);

	read_value(pin, delay)
}
pub fn read_value<P: InputPin + OutputPin>(pin: &mut P, delay: &mut Delay) -> i8 {
	let mut value: i8 = 0;
	let mut counter = 1;
	let mut state = InputPin::is_high(pin);

	for n in 0..8 {
		state = InputPin::is_high(pin);
		counter <<= 1;											//The shift operation in the end of the loop? Teo said start at 0,  why?
		if state == true {
			value = value | counter;
		};
		delay.delay_ms(500u16);
	};
	value
}*/

#[cfg(test)]
mod tests {
    #[test]
    fn test_write_pin() {
        assert_eq!(2 + 2, 4);
    }
}
