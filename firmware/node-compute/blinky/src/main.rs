// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_semihosting;
extern crate panic_semihosting;
extern crate picorunner;
extern crate picotalk;
extern crate stm32f0x0;

use cortex_m::asm;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use stm32f0x0::Peripherals;

use picorunner::*;

fn main() {
    let mut out = hio::hstdout().unwrap();
    let peripherals = Peripherals::take().unwrap();
    let gpioa = peripherals.GPIOA;
    let rcc = peripherals.RCC;

    rcc.ahbenr.modify(|_, w| w.iopaen().set_bit()); // enable IO port A clock
    unsafe {
        gpioa.moder.modify(|_, w| w.moder4().bits(1)); // output
    }
	
    let mut interpreter = Interpreter::new();
	let memreg = MemRegister::Acc;
	memreg.write(&mut interpreter, 5);
    let mut state = false;



    //writeln!(out, "The value is {}", memreg.read(&interpreter));
	
    if memreg.read(&mut interpreter) == 5 {
        loop {
            state = !state;
            gpioa.odr.modify(|_, w| w.odr4().bit(state));

            // asm::bkpt();
            for _i in 0..1000000 {
                //asm::nop();
            }
        }
    }
}
