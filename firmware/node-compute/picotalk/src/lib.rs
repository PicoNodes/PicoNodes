// File for communicating between PicoNodes
//Author Therese Kennerberg <tke@kth.se> and Teo Klestrup Röijezon <teo@nullable.se>

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

//Take an OutputPin, value and the clock as argument and sends the value on the given pin bitwise of a i8.
pub fn write_pin<P: OutputPin>(pin: &mut P, value: i8, delay: &mut Delay) {
	
	//Can only send i8 values between 100..-100
    let mut state = true;
	let mut n = 1;
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


pub fn read_pin<P: InputPin + OutputPin>(pin: &mut P, delay: &mut Delay) -> i8 {
	let mut value: i8 = 0;
	let mut state = InputPin::is_high(pin);
	let mut counter = 1;
	
	for n in 0..8 {
		state = InputPin::is_high(pin);
		counter <<= 1;
		if state == true {
			value = value | counter;
		};
	delay.delay_ms(500u16);
	};
	value
}

    /*loop {
        state = !state;
        if state {
            pin.set_high();
        } else {
            pin.set_low();
        }
        
		//writeln!(out, "O: {}, I: {}", OutputPin::is_high(&pin), InputPin::is_high(&pin)).unwrap();

        //delay.delay_ms(500u16);
    }*/

#[cfg(test)]
mod tests {
    #[test]
    fn test_write_pin() {
        assert_eq!(2 + 2, 4);
    }
}
