// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_semihosting;
extern crate panic_semihosting;
extern crate stm32f0x0;

use cortex_m::asm;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use stm32f0x0::Peripherals;

fn main() {
    let mut out = hio::hstdout().unwrap();
    let peripherals = Peripherals::take().unwrap();
    let gpioa = peripherals.GPIOA;
    let rcc = peripherals.RCC;

    rcc.ahbenr.modify(|_, w| w.iopaen().set_bit()); // enable IO port A clock
    unsafe {
        gpioa.otyper.modify(|_, w| w.ot4().set_bit()); // open drain
        gpioa.moder.modify(|_, w| w.moder4().bits(1)); // output
    }

    let mut state = true;
    loop {
        state = !state;
        gpioa.odr.modify(|_, w| w.odr4().bit(state));
        writeln!(out, "O: {}, I: {}", gpioa.odr.read().odr4().bit(), gpioa.idr.read().idr4().bit()).unwrap();

        // asm::bkpt();
        for _i in 0..1000000 {
            asm::nop();
        }
    }
}
