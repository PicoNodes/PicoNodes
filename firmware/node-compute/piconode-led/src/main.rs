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

use rtfm::{app, Threshold};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::serial::{Rx, Tx, Serial, Event as SerialEvent};
use stm32f0x0_hal::gpio::{Output, OpenDrain, gpioa::*, gpiof::PF0};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

fn picotalk_tx_tick(_t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_TX_STATE;
    let mut pin = r.PICOTALK_TX_PIN;
    let mut timer = r.PICOTALK_TX_TIMER;

    timer.wait().unwrap();

    picotalk::transmit_value(&mut *pin, &mut state, 10);
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


    let pa2 = gpioa.pa2.into_pull_down_input(&mut gpioa.moder, &mut gpioa.afrl);        //Led 1
    let pa3 = gpioa.pa3.into_pull_down_input(&mut gpioa.moder, &mut gpioa.afrl);        //Led 2
    let pa7 = gpioa.pa7.into_pull_down_input(&mut gpioa.moder, &mut gpioa.afrl);        //Led 3
    let pa6 = gpioa.pa6.into_pull_down_input(&mut gpioa.moder, &mut gpioa.afrl);        //Led 4
}

fn idle() -> ! {
    loop {
        if
        rtfm::wfi();
    }
}

app! {
    device: stm32f0x0,
    resources: {

        static LED_1: PA2<Input<PullDown>>;
        static LED_2: PA3<Input<PullDown>>;
        static LED_3: PA7<Input<PullDown>>;
        static LED_4: PA6<Input<PullDown>>;
        //static SERIAL1_RX: Rx<stm32f0x0::USART1>;
        //static SERIAL1_TX: Tx<stm32f0x0::USART1>;
        //Resources for transmitting a value
        //static PICOTALK_TX_PIN: PA4<Output<OpenDrain>>;
        //static PICOTALK_TX_STATE: picotalk::TransmitState = picotalk::TransmitState::HandshakeAdvertise(0);
        //static PICOTALK_TX_TIMER: Timer<stm32f0x0::TIM3>;
        //Resources for recieving a value from a pin
        //static PICOTALK_RX_PIN: PF0<Output<OpenDrain>>;
        //static PICOTALK_RX_STATE: picotalk::RecieveState = picotalk::RecieveState::HandshakeListen(0);
        //static PICOTALK_RX_TIMER: Timer<stm32f0x0::TIM14>;

        //static STORE: PicoStore;
    },

}
