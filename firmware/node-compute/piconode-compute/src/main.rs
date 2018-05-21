// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro, asm)]
#![no_std]

extern crate cortex_m;          //Low level access to the cortex-m processor
extern crate cortex_m_rt;       //Runtime for cortex-m microcontrollers
extern crate cortex_m_rtfm as rtfm;   //Real Time For the Masses framework for thhe ARM-cortex
extern crate cortex_m_semihosting;  //Enables coderunning on an ARM-target to use input/output pins
extern crate stm32f0x0_hal;     //HAL for the stm32f0x0 family. Implementation of the embedded hal traits
extern crate embedded_hal;      //Hardware abstraction layer for embedded systems
extern crate picostorm;         //Enables seriecommunication with the ESP32 HUZZAH
extern crate picotalk;      //Enables communication between the nodes
extern crate picorunner;        //Run PicoInstsructions
extern crate picostore;     //Storing/fetching the instructions from the programmer

#[cfg(feature = "debug")]
extern crate panic_semihosting;

#[cfg(not(feature = "debug"))]
extern crate panic_abort;

#[macro_use]
extern crate nb;

use picostore::PicoStore;
use picorunner::IoPinout;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use rtfm::{app, Threshold, Resource};
#[allow(unused)]
use cortex_m::asm;

use embedded_hal::digital::InputPin;
use embedded_hal::timer::CountDown;

use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0::{self, TIM3};
use stm32f0x0_hal::serial::{Rx, Tx, Serial, Event as SerialEvent, Error as SerialError};
use stm32f0x0_hal::gpio::{Output, OpenDrain, gpioa::{PA1, PA4, PA5}, gpiob::PB1};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

pub struct PicotalkPinout;
impl IoPinout for PicotalkPinout {
    type Down = PA4<Output<OpenDrain>>;
    type Left = PA1<Output<OpenDrain>>;
    type Up = PB1<Output<OpenDrain>>;
    type Right = PA5<Output<OpenDrain>>;
    type TimerResource = idle::PICOTALK_TIMER;
    type Timer = Timer<TIM3>;
    type TimerUnit = <Timer<TIM3> as CountDown>::Time;
}

fn handle_picostorm_msg(_t: &mut Threshold, r: USART1::Resources) {
    let mut rx = r.SERIAL1_RX;
    let mut tx = r.SERIAL1_TX;
    let mut store = r.STORE;
    let mut reset = r.RESET;

    // embedded-hal doesn't have a flash abstraction yet :(
    let flash = unsafe { (stm32f0x0::FLASH::ptr() as *mut stm32f0x0::flash::RegisterBlock).as_mut().unwrap() };

    // embedded-hal doesn't have a crc abstraction yet :(
    let crc_peripheral = unsafe { (stm32f0x0::CRC::ptr() as *mut stm32f0x0::crc::RegisterBlock).as_mut().unwrap() };

    let cmd = match picostorm::Command::read(&mut *rx) {
        // Disconnected, ignore message
        Err(picostorm::ReadError::Serial(SerialError::Framing)) => return,
        x => x.unwrap(),
    };

    // let mut out = hio::hstdout().unwrap();
    match cmd {
        picostorm::Command::DownloadBytecode { ref bytecode } => {
            // writeln!(out, "download bytecode").unwrap();
            store.replace(bytecode, flash);
            let crc = store.crc(crc_peripheral);
            let done_event = picostorm::Event::DownloadedBytecode { crc };
            done_event.write(&mut *tx).unwrap();
            *reset = true;
        },
        picostorm::Command::Ping => {
            // writeln!(out, "ping!").unwrap();
        },
    }
}

fn init(p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let rcc = p.device.RCC;
    rcc.ahbenr.modify(|_,w| w.crcen().set_bit());

    let mut rcc = rcc.constrain();
    let mut flash = p.device.FLASH.constrain();
    let clocks = rcc.cfgr
        .sysclk(8.mhz())
        .hclk(8.mhz())
        .pclk1(8.mhz())
        .pclk2(8.mhz())
        .freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);
    let mut gpiob = p.device.GPIOB.split(&mut rcc.ahb);

    let usart1_pin_tx = gpioa.pa9.into_af1(&mut gpioa.moder, &mut gpioa.afrh);
    let usart1_pin_rx = gpioa.pa10.into_af1(&mut gpioa.moder, &mut gpioa.afrh);

    let mut pa1 = gpioa.pa1.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    let mut pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    let mut pa5 = gpioa.pa5.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    let mut pb1 = gpiob.pb1.into_open_drain_output(&mut gpiob.moder, &mut gpiob.otyper);

    pa1.set_high();
    pa4.set_high();
    pa5.set_high();
    pb1.set_high();

    while InputPin::is_low(&pa1) {}
    while InputPin::is_low(&pa4) {}
    while InputPin::is_low(&pa5) {}
    while InputPin::is_low(&pb1) {}

    let tim3 = Timer::tim3(p.device.TIM3, 10.khz(), clocks, &mut rcc.apb1);

    let usart1 = p.device.USART1;
    let mut serial = Serial::usart1(usart1, (usart1_pin_tx, usart1_pin_rx), 115_200.bps(), clocks, &mut rcc.apb2);
    serial.listen(SerialEvent::Rxne);
    let (tx, rx) = serial.split();

    init::LateResources {
        SERIAL1_RX: rx,
        SERIAL1_TX: tx,
        PICOTALK_TIMER: tim3,
        PICOTALK_PIN_DOWN: pa4,
        PICOTALK_PIN_LEFT: pa1,
        PICOTALK_PIN_UP: pb1,
        PICOTALK_PIN_RIGHT: pa5,
        STORE: PicoStore::take().unwrap(),
    }
}

fn idle(t: &mut Threshold, r: idle::Resources) -> ! {
    let store = r.STORE;
    let mut reset = r.RESET;

    let mut interpreter = picorunner::Interpreter::<PicotalkPinout>::new(
        r.PICOTALK_PIN_DOWN,
        r.PICOTALK_PIN_LEFT,
        r.PICOTALK_PIN_UP,
        r.PICOTALK_PIN_RIGHT,
        r.PICOTALK_TIMER,
    );
    // let mut out = hio::hstdout().unwrap();

    loop {
        let mut instruction_bytes: [u8; picorunner::INSTRUCTION_BYTES] = [0; picorunner::INSTRUCTION_BYTES];
        store.claim(t, |store, t| {
            let mut reset = reset.borrow_mut(t);
            if *reset {
                *reset = false;
                interpreter.prog_counter = 0;
            } else if interpreter.prog_counter >= store.len() {
                interpreter.prog_counter = 0;
            }
            let slice = &store[interpreter.prog_counter ..];
            if slice.len() >= 3 {
                for (i, byte) in slice.iter().take(3).enumerate() {
                    instruction_bytes[i] = *byte;
                }
            } else {
                // instruction_bytes = [0, 0, 127]; // No-op instruction
                instruction_bytes = [0, 130, 131]; // MOV left right
            }
        });
        let instruction = picorunner::decode_instruction(&instruction_bytes);
        if let Some(instruction) = instruction {
            picorunner::run_instruction(instruction, &mut interpreter, t);
        }
    }
}

app! {
    device: stm32f0x0,
    resources: {
        static SERIAL1_RX: Rx<stm32f0x0::USART1>;
        static SERIAL1_TX: Tx<stm32f0x0::USART1>;

        static PICOTALK_TIMER: <PicotalkPinout as IoPinout>::Timer;
        static PICOTALK_PIN_DOWN: <PicotalkPinout as IoPinout>::Down;
        static PICOTALK_PIN_LEFT: <PicotalkPinout as IoPinout>::Left;
        static PICOTALK_PIN_UP: <PicotalkPinout as IoPinout>::Up;
        static PICOTALK_PIN_RIGHT: <PicotalkPinout as IoPinout>::Right;

        static STORE: PicoStore;
        static RESET: bool = false;
    },
    idle: {
        resources: [STORE, PICOTALK_TIMER, PICOTALK_PIN_DOWN, PICOTALK_PIN_LEFT, PICOTALK_PIN_UP, PICOTALK_PIN_RIGHT, RESET],
    },
    tasks: {
        USART1: {
            path: handle_picostorm_msg,
            resources: [SERIAL1_RX, SERIAL1_TX, STORE, PICOTALK_TIMER, RESET],
            priority: 2,
        },

        // TIM3: {
        //     path: picotalk_tx_tick,
        //     resources: [PICOTALK_TX_PIN, PICOTALK_TX_STATE, PICOTALK_TX_TIMER],
        //     priority: 6,
        // },

        // TIM14: {
        //     path: picotalk_rx_tick,
        //     resources: [PICOTALK_RX_PIN, PICOTALK_RX_STATE, PICOTALK_RX_TIMER],
        //     priority: 6,
        // },
    }
}
