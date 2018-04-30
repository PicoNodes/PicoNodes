// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_rtfm as rtfm;
extern crate cortex_m_semihosting;
extern crate stm32f0x0_hal;
extern crate embedded_hal;
extern crate picostorm;
extern crate picotalk;
extern crate picorunner;
extern crate picostore;

#[cfg(feature = "debug")]
extern crate panic_semihosting;

#[cfg(not(feature = "debug"))]
extern crate panic_abort;

#[macro_use]
extern crate nb;

use picostore::PicoStore;

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

fn picotalk_tick(_t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_STATE;
    let mut pin = r.PICOTALK_PIN;
    let mut timer = r.PICOTALK_TIMER;

    timer.wait().unwrap();
    picotalk::transmit_value(&mut *pin, &mut state, 15);
}

fn handle_picostorm_msg(_t: &mut Threshold, r: USART1::Resources) {
    let mut rx = r.SERIAL1_RX;
    let mut tx = r.SERIAL1_TX;
    let mut store = r.STORE;

    // embedded-hal doesn't have a flash abstraction yet :(
    let flash = unsafe { (stm32f0x0::FLASH::ptr() as *mut stm32f0x0::flash::RegisterBlock).as_mut().unwrap() };

    // embedded-hal doesn't have a crc abstraction yet :(
    let crc_peripheral = unsafe { (stm32f0x0::CRC::ptr() as *mut stm32f0x0::crc::RegisterBlock).as_mut().unwrap() };

    let cmd = picostorm::Command::read(&mut *rx).unwrap();

    let mut out = hio::hstdout().unwrap();
    match cmd {
        picostorm::Command::DownloadBytecode { ref bytecode } => {
            writeln!(out, "download bytecode").unwrap();
            store.replace(bytecode, flash);
            let crc = store.crc(crc_peripheral);
            let done_event = picostorm::Event::DownloadedBytecode { crc };
            done_event.write(&mut *tx).unwrap();
        },
        picostorm::Command::Ping => {
            writeln!(out, "ping!").unwrap();
        },
    }
}

fn init(p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let rcc = p.device.RCC;
    rcc.ahbenr.modify(|_,w| w.crcen().set_bit());

    let mut rcc = rcc.constrain();
    let mut flash = p.device.FLASH.constrain();
    let clocks = rcc.cfgr.freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);

    let pa2 = gpioa.pa2.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa3 = gpioa.pa3.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);

    let mut tim3 = Timer::tim3(p.device.TIM3, 1.hz(), clocks, &mut rcc.apb1);
    tim3.listen(TimerEvent::TimeOut);

    let usart1 = p.device.USART1;
    let mut serial = Serial::usart1(usart1, (pa2, pa3), 115_200.bps(), clocks, &mut rcc.apb2);
    serial.listen(SerialEvent::Rxne);
    let (tx, rx) = serial.split();

    init::LateResources {
        SERIAL1_RX: rx,
        SERIAL1_TX: tx,
        PICOTALK_PIN: pa4,
        PICOTALK_TIMER: tim3,
        STORE: PicoStore::take().unwrap(),
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

        static PICOTALK_PIN: PA4<Output<OpenDrain>>;
        static PICOTALK_STATE: picotalk::TransmitState = picotalk::TransmitState::HandshakeAdvertise(0);
        static PICOTALK_TIMER: Timer<stm32f0x0::TIM3>;

        static STORE: PicoStore;
    },
    tasks: {
        USART1: {
            path: handle_picostorm_msg,
            resources: [SERIAL1_RX, SERIAL1_TX, STORE],
            priority: 2,
        },

        TIM3: {
            path: picotalk_tick,
            resources: [PICOTALK_PIN, PICOTALK_STATE, PICOTALK_TIMER],
            priority: 1,
        },
    }
}
