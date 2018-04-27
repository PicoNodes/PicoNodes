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
extern crate stm32f0x0_hal;

use cortex_m::asm;

use core::fmt::Write;
use cortex_m_semihosting::hio;
use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::delay::Delay;
use stm32f0x0_hal::stm32f0x0::{CorePeripherals, Peripherals};

use picotalk::*;
use picorunner::*;

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
	let value = 15;
    let mut state = TransmitState::HandshakeAdvertise(0);

	loop {
	    transmitting_value(&mut pa4, &mut state, value);
        delay.delay_ms(1u16);
	}

    /*let mut out = hio::hstdout().unwrap();
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
    }*/
}
