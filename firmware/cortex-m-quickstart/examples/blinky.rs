#![feature(used)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_semihosting;
extern crate stm32f30x;

use core::fmt::Write;

use cortex_m::asm;
use cortex_m_semihosting::hio;

use stm32f30x::Peripherals;

static mut ctr: u16 = 0;

#[inline(never)]
fn main() {
    let peripherals = Peripherals::take().unwrap();
    let mut stdout = hio::hstdout().unwrap();

    let gpioe = peripherals.GPIOE;
    let rcc = peripherals.RCC;

    rcc.ahbenr.modify(|_, w| w.iopeen().enabled());

    gpioe.moder.modify(|_, w| w
                       .moder9().output()
                       .moder14().output());

    let mut state = false;
    loop {
        unsafe {
            ctr += 1;
            writeln!(stdout, "{}", ctr).unwrap();
        }

        gpioe.odr.modify(|_, w| w.odr9().bit(state));
        gpioe.odr.modify(|_, w| w.odr14().bit(!state));
        state = !state;

        for _i in 0..10000 {
            asm::nop();
        }
    }
}

#[used]
#[link_section = ".vector_table.interrupts"]
static INTERRUPTS: [extern "C" fn(); 240] = [default_handler; 240];

extern "C" fn default_handler() {
    asm::bkpt();
}
