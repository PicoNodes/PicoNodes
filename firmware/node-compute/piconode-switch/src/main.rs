// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
#![no_std]

extern crate cortex_m;          //Low level access to the cortex-m processor
extern crate cortex_m_rt;       //Runtime for cortex-m microcontrollers
extern crate cortex_m_rtfm as rtfm;   //Real Time For the Masses framework for thhe ARM-cortex
extern crate cortex_m_semihosting;  //Enables coderunning on an ARM-target to use input/output pins
extern crate stm32f0x0_hal;     //HAL for the stm32f0x0 family. Implementation of the embedded hal traits
extern crate embedded_hal;      //Hardware abstraction layer for embedded systems
extern crate picotalk;      //Enables communication between the nodes

#[cfg(feature = "debug")]
extern crate panic_semihosting;

#[cfg(not(feature = "debug"))]
extern crate panic_abort;

#[macro_use]
extern crate nb;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use rtfm::{app, Threshold, Resource};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::serial::{Rx, Tx, Serial, Event as SerialEvent, Error as SerialError};
use stm32f0x0_hal::gpio::{Output, Input, PullDown, PushPull, OpenDrain, gpioa::*, gpiof::PF0};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

fn picotalk_tx_tick(t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_TX_STATE;
    let mut pin = r.PICOTALK_TX_RIGHT;
    let mut timer = r.PICOTALK_TX_TIMER;
    let value = r.VALUE;

    timer.wait().unwrap();
    let value = value.claim(t, |value, _t| {
        *value
    });

    picotalk::transmit_value(&mut *pin, &mut state, value);
}

//Sheck if any of the switches is pressed
fn piconode_check_switch(_t: &mut Threshold, r: TIM14::Resources) {
    let mut timer = r.PICONODE_CHECK_SWITCH_TIMER;
    let mut value = r.VALUE;

    let mut switch_1 = r.SWITCH_1_PIN;
    let mut switch_2 = r.SWITCH_2_PIN;
    let mut switch_3 = r.SWITCH_3_PIN;
    let mut switch_4 = r.SWITCH_4_PIN;

    timer.wait().unwrap();

    if switch_1.is_high() {
        *value = 1;
    } else if switch_2.is_high() {
        *value = 2;
    } else if switch_3.is_high() {
        *value = 3;
    } else if switch_4.is_high() {
        *value = 4;
    } else {
        *value = 0;
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

    let mut pa5 = gpioa.pa5.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    pa5.set_high();

    let mut tim3 = Timer::tim3(p.device.TIM3, 10.khz(), clocks, &mut rcc.apb1);     //timer for transmitting value
    let mut tim14 = Timer::tim14(p.device.TIM14, 10.khz(), clocks, &mut rcc.apb1);  //timer for checking switches
    tim3.listen(TimerEvent::TimeOut);
    tim14.listen(TimerEvent::TimeOut);

    let mut pa2 = gpioa.pa2.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 1
    let mut pa3 = gpioa.pa3.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 2
    let mut pa7 = gpioa.pa7.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 3
    let mut pa6 = gpioa.pa6.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 4

    init::LateResources {
        PICONODE_CHECK_SWITCH_TIMER: tim14,     //timer for checking the switches
        PICOTALK_TX_TIMER: tim3,                //interupt for transmitting the value recieved from switch
        PICOTALK_TX_RIGHT: pa5,                 //transmission pin


        SWITCH_1_PIN: pa2,      //responds for the value of one
        SWITCH_2_PIN: pa3,      //responds for the value of two
        SWITCH_3_PIN: pa7,      //responds for the value of three
        SWITCH_4_PIN: pa6,      //responds for the value of four
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
        //Resources for transmitting a value
        static PICOTALK_TX_RIGHT: PA5<Output<OpenDrain>>;
        static PICOTALK_TX_STATE: picotalk::TransmitState = picotalk::TransmitState::HandshakeAdvertise(0);
        static PICOTALK_TX_TIMER: Timer<stm32f0x0::TIM3>;

        static PICONODE_CHECK_SWITCH_TIMER: Timer<stm32f0x0::TIM14>;
        static VALUE: i8 = 0;

        static SWITCH_1_PIN: PA2<Input<PullDown>>;    //PA<Input<PullDown>> for switch
        static SWITCH_2_PIN: PA3<Input<PullDown>>;
        static SWITCH_3_PIN: PA7<Input<PullDown>>;
        static SWITCH_4_PIN: PA6<Input<PullDown>>;
    },
    tasks: {
        TIM3: {
            path: picotalk_tx_tick,
            resources: [VALUE, PICOTALK_TX_RIGHT, PICOTALK_TX_STATE, PICOTALK_TX_TIMER],
            priority: 1,
        },
        TIM14: {
            path: piconode_check_switch,
            resources: [VALUE, PICONODE_CHECK_SWITCH_TIMER, SWITCH_1_PIN, SWITCH_2_PIN, SWITCH_3_PIN, SWITCH_4_PIN],
            priority: 1,
        }
    }
}
