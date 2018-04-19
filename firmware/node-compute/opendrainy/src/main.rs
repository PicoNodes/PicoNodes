// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_semihosting;
extern crate panic_semihosting;
extern crate stm32f0x0_hal;
extern crate embedded_hal;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use embedded_hal::digital::*;

use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::delay::Delay;
use stm32f0x0_hal::stm32f0x0::{CorePeripherals, Peripherals};

fn main() {
    let mut out = hio::hstdout().unwrap();
    let peripherals = Peripherals::take().unwrap();
    let core_peripherals = CorePeripherals::take().unwrap();
    let mut flash = peripherals.FLASH.constrain();
    let mut rcc = peripherals.RCC.constrain();
    let clocks = rcc.cfgr.freeze(&mut flash.acr);

    let mut gpioa = peripherals.GPIOA.split(&mut rcc.ahb);
    let mut delay = Delay::new(core_peripherals.SYST, clocks);

    let mut pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);

    let mut state = true;
    loop {
        state = !state;
        if state {
            pa4.set_high();
        } else {
            pa4.set_low();
        }
        writeln!(out, "O: {}, I: {}", OutputPin::is_high(&pa4), InputPin::is_high(&pa4)).unwrap();

        delay.delay_ms(500u16);
    }
}
