// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
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

use core::fmt::Write;
use cortex_m_semihosting::hio;

use rtfm::{app, Threshold, Resource};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::serial::{Rx, Tx, Serial, Event as SerialEvent, Error as SerialError};
use stm32f0x0_hal::gpio::{Output, OpenDrain, gpioa::PA4, gpiof::PF0};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

fn picotalk_tx_tick(_t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_TX_STATE;
    let mut pin = r.PICOTALK_TX_PIN;
    let mut timer = r.PICOTALK_TX_TIMER;

    timer.wait().unwrap();
    picotalk::transmit_value(&mut *pin, &mut state, 10);
}

//To test the recieve function
fn picotalk_rx_tick(_t: &mut Threshold, r: TIM14::Resources) {
    let mut state = r.PICOTALK_RX_STATE;
    let mut pin = r.PICOTALK_RX_PIN;
    let mut timer = r.PICOTALK_RX_TIMER;

    timer.wait().unwrap();
    picotalk::recieve_value(&mut *pin, &mut *state);
    if let picotalk::RecieveState::Done(a) = *state {
        let mut out = hio::hstdout().unwrap();
        writeln!(out, "The recieved value is: {}", a).unwrap();
    }
}

fn handle_picostorm_msg(_t: &mut Threshold, r: USART1::Resources) {
    let mut rx = r.SERIAL1_RX;
    let mut tx = r.SERIAL1_TX;
    let mut store = r.STORE;

    // embedded-hal doesn't have a flash abstraction yet :(
    let flash = unsafe { (stm32f0x0::FLASH::ptr() as *mut stm32f0x0::flash::RegisterBlock).as_mut().unwrap() };

    // embedded-hal doesn't have a crc abstraction yet :(
    let crc_peripheral = unsafe { (stm32f0x0::CRC::ptr() as *mut stm32f0x0::crc::RegisterBlock).as_mut().unwrap() };

    let cmd = match picostorm::Command::read(&mut *rx) {
        // Disconnected, ignore message
        Err(picostorm::ReadError::Serial(SerialError::Framing)) => return,
        x => x.unwrap(),
    };

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
    let clocks = rcc.cfgr
        .sysclk(8.mhz())
        .hclk(8.mhz())
        .pclk1(8.mhz())
        .pclk2(8.mhz())
        .freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);
    let mut gpiof = p.device.GPIOF.split(&mut rcc.ahb);

    let usart1_pin_tx = gpioa.pa9.into_af1(&mut gpioa.moder, &mut gpioa.afrh);
    let usart1_pin_rx = gpioa.pa10.into_af1(&mut gpioa.moder, &mut gpioa.afrh);

    let mut pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    let mut pf0 = gpiof.pf0.into_open_drain_output(&mut gpiof.moder, &mut gpiof.otyper);

    pa4.set_high();
    pf0.set_high();

    let mut tim3 = Timer::tim3(p.device.TIM3, 10.khz(), clocks, &mut rcc.apb1);
    let mut tim14 = Timer::tim14(p.device.TIM14, 10.khz(), clocks, &mut rcc.apb1);
    tim3.listen(TimerEvent::TimeOut);
    tim14.listen(TimerEvent::TimeOut);

    let usart1 = p.device.USART1;
    let mut serial = Serial::usart1(usart1, (usart1_pin_tx, usart1_pin_rx), 115_200.bps(), clocks, &mut rcc.apb2);
    serial.listen(SerialEvent::Rxne);
    let (tx, rx) = serial.split();

    init::LateResources {
        SERIAL1_RX: rx,
        SERIAL1_TX: tx,
        PICOTALK_TX_PIN: pa4,
        PICOTALK_TX_TIMER: tim3,
        PICOTALK_RX_PIN: pf0,
        PICOTALK_RX_TIMER: tim14,
        STORE: PicoStore::take().unwrap(),
    }
}

fn idle(t: &mut Threshold, r: idle::Resources) -> ! {
    let store = r.STORE;
    let mut interpreter = picorunner::Interpreter::new();
    let mut out = hio::hstdout().unwrap();

    loop {
        interpreter.prog_counter %= picostore::PICOSTORE_BYTES;
        let mut instruction_bytes: [u8; picorunner::INSTRUCTION_BYTES] = [0; picorunner::INSTRUCTION_BYTES];
        store.claim(t, |store, _t| {
            let slice = &store[interpreter.prog_counter .. interpreter.prog_counter + 3];
            for (i, byte) in slice.iter().enumerate() {
                instruction_bytes[i] = *byte;
            }
        });
        let instruction = picorunner::decode_instruction(&instruction_bytes);
        if let Some(instruction) = instruction {
            picorunner::run_instruction(instruction, &mut interpreter);
        }
        writeln!(out, "Acc: {}", interpreter.reg_acc).unwrap();
    }
}

app! {
    device: stm32f0x0,
    resources: {
        static SERIAL1_RX: Rx<stm32f0x0::USART1>;
        static SERIAL1_TX: Tx<stm32f0x0::USART1>;
        //Resources for transmitting a value
        static PICOTALK_TX_PIN: PA4<Output<OpenDrain>>;
        static PICOTALK_TX_STATE: picotalk::TransmitState = picotalk::TransmitState::HandshakeAdvertise(0);
        static PICOTALK_TX_TIMER: Timer<stm32f0x0::TIM3>;
        //Resources for recieving a value from a pin
        static PICOTALK_RX_PIN: PF0<Output<OpenDrain>>;
        static PICOTALK_RX_STATE: picotalk::RecieveState = picotalk::RecieveState::HandshakeListen(0);
        static PICOTALK_RX_TIMER: Timer<stm32f0x0::TIM14>;

        static STORE: PicoStore;
    },
    idle: {
        resources: [STORE],
    },
    tasks: {
        USART1: {
            path: handle_picostorm_msg,
            resources: [SERIAL1_RX, SERIAL1_TX, STORE],
            priority: 2,
        },

        TIM3: {
            path: picotalk_tx_tick,
            resources: [PICOTALK_TX_PIN, PICOTALK_TX_STATE, PICOTALK_TX_TIMER],
            priority: 6,
        },

        TIM14: {
            path: picotalk_rx_tick,
            resources: [PICOTALK_RX_PIN, PICOTALK_RX_STATE, PICOTALK_RX_TIMER],
            priority: 6,
        },
    }
}
