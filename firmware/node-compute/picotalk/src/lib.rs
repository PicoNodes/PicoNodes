// File for communicating between PicoNodes

#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_semihosting;

extern crate stm32f0x0_hal;

use core::fmt::Write;

use stm32f0x0_hal::gpio::gpioa::PA4;
use stm32f0x0_hal::gpio::*;



/*let mut out = hio::hstdout().unwrap();
let peripherals = Peripherals::take().unwrap();
let gpioa = peripherals.GPIOA;
let rcc = peripherals.RCC;*/


pub fn write_pin(pin: PA4<Input<Floating>>) {
	
    let mut state = false;
	let value = 5;

}



#[cfg(test)]
mod tests {
    #[test]
    fn test_write_pin() {
        assert_eq!(2 + 2, 4);
    }
}
