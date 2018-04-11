#![feature(used)]
#![no_std]

extern crate panic_semihosting;
extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_semihosting;
extern crate stm32f0x0;

use cortex_m::asm;

use stm32f0x0::Peripherals;

fn main() {
    let peripherals = Peripherals::take().unwrap();
    let gpioa = peripherals.GPIOA;
    let rcc = peripherals.RCC;

    rcc.ahbenr.modify(|_, w| w.iopaen().set_bit()); // enable IO port A clock
    unsafe {
        gpioa.moder.modify(|_, w| w.moder4().bits(1)); // output
    }

    let mut state = false;
    loop {
        state = !state;
        gpioa.odr.modify(|_, w| w.odr4().bit(state));

        // asm::bkpt();
        for _i in 0..10000 {
            asm::nop();
        }
    }
}
