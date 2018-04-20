// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_rtfm as rtfm;
extern crate cortex_m_semihosting;
extern crate panic_semihosting;
extern crate stm32f0x0_hal;
extern crate embedded_hal;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use rtfm::{app, Threshold};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::serial::{Rx, Tx, Serial, Event as SerialEvent};
use stm32f0x0_hal::gpio::{Output, OpenDrain, gpioa::PA4};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

fn loopback(_t: &mut Threshold, r: USART1::Resources) {
    // asm::bkpt();

    // let mut out = hio::hstdout().unwrap();
    let mut rx = r.SERIAL1_RX;
    let mut tx = r.SERIAL1_TX;

    // writeln!(out, "{}", rx.read().unwrap() as char).unwrap();
    tx.write(rx.read().unwrap()).unwrap();
}

fn blink(_t: &mut Threshold, r: TIM16::Resources) {
    // asm::bkpt();

    let mut timer = r.BLINKY_TIMER;
    let mut state = r.BLINKY_STATE;
    let mut ctr = r.BLINKY_CTR;
    let mut pin = r.BLINKY_PIN;

    if *ctr < 100000 {
        *ctr += 1;
    } else {
        *ctr = 0;

        let mut out = hio::hstdout().unwrap();
        *state = !*state;
        if *state {
            pin.set_high();
            // r.SERIAL1_TX.write('1' as u8).unwrap();
        } else {
            pin.set_low();
            // writeln!(out, "Disabling clock interrupt").unwrap();
            // timer.unlisten(TimerEvent::TimeOut);
            // r.SERIAL1_TX.write('0' as u8).unwrap();
        }
    }
}

fn init(p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let mut rcc = p.device.RCC.constrain();
    let mut flash = p.device.FLASH.constrain();
    let clocks = rcc.cfgr.freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);

    let pa2 = gpioa.pa2.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa3 = gpioa.pa3.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);

    let mut tim16 = Timer::tim16(p.device.TIM16, 1.hz(), clocks, &mut rcc.apb2);
    tim16.listen(TimerEvent::TimeOut);

    let usart1 = p.device.USART1;
    let mut serial = Serial::usart1(usart1, (pa2, pa3), 115_200.bps(), clocks, &mut rcc.apb2);
    serial.listen(SerialEvent::Rxne);
    let (tx, rx) = serial.split();

    init::LateResources {
        SERIAL1_RX: rx,
        SERIAL1_TX: tx,
        BLINKY_TIMER: tim16,
        BLINKY_PIN: pa4,
    }
}

fn idle() -> ! {
    loop {
        rtfm::wfi();
    }
}

app! {
    device: stm32f0x0,
    resources: {
        static SERIAL1_RX: Rx<stm32f0x0::USART1>;
        static SERIAL1_TX: Tx<stm32f0x0::USART1>;

        static BLINKY_TIMER: Timer<stm32f0x0::TIM16>;
        static BLINKY_STATE: bool = false;
        static BLINKY_CTR: u32 = 0;
        static BLINKY_PIN: PA4<Output<OpenDrain>>;
    },
    tasks: {
        USART1: {
            path: loopback,
            resources: [SERIAL1_RX, SERIAL1_TX],
            priority: 2,
        },

        TIM16: {
            path: blink,
            resources: [BLINKY_TIMER, BLINKY_STATE, BLINKY_CTR, BLINKY_PIN],
            priority: 1,
        }
    }
}
