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
use stm32f0x0_hal::gpio::{Output, PushPull, OpenDrain, gpioa::*};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

//Recieving from four directions need four timers tim14, tim3, tim16, tim17
fn picotalk_rx_tick(_t: &mut Threshold, r: TIM14::Resources) {
    let mut state = r.PICOTALK_RX_STATE;
    let mut pin = r.PICOTALK_RX_LEFT;
    let mut timer = r.PICOTALK_RX_TIMER;

    timer.wait().unwrap();
    picotalk::recieve_value(&mut *pin, &mut *state);
    if let picotalk::RecieveState::Done(value) = *state {
        let mut out = hio::hstdout().unwrap();
        writeln!(out, "The recieved value is: {}", value).unwrap();
    }
}

//Lighting the LED after recieving the recieve_value
fn piconode_lighting_led(t: &mut Threshold, r: TIM3::Resources) {
    let mut led_1 = r.LED_1_PIN;
    let mut led_2 = r.LED_2_PIN;
    let mut led_3 = r.LED_3_PIN;
    let mut led_4 = r.LED_4_PIN;
    let value = r.VALUE_LEFT;
    let mut timer = r.PICONODE_LED_TIMER;

    timer.wait().unwrap();
    let value = value.claim(t, |value, _t| {
        *value
    });

    match value {
        1 => {
            led_2.set_low();
            led_3.set_low();
            led_4.set_low();
            led_1.set_high();
        },
        2 => {
            led_1.set_low();
            led_3.set_low();
            led_4.set_low();
            led_2.set_high();
        },
        3 => {
            led_1.set_low();
            led_2.set_low();
            led_4.set_low();
            led_3.set_high();
        },
        4 => {
            led_1.set_low();
            led_2.set_low();
            led_3.set_low();
            led_4.set_high();
        },
        _ => {
            led_1.set_low();
            led_2.set_low();
            led_3.set_low();
            led_4.set_low();
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

    let mut pa1 = gpioa.pa1.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    pa1.set_high();

    let mut tim3 = Timer::tim3(p.device.TIM3, 10.khz(), clocks, &mut rcc.apb1);
    let mut tim14 = Timer::tim14(p.device.TIM14, 10.khz(), clocks, &mut rcc.apb1);
    // tim3.listen(TimerEvent::TimeOut);
    // tim14.listen(TimerEvent::TimeOut);

    let pa2 = gpioa.pa2.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 1
    let pa3 = gpioa.pa3.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 2
    let pa7 = gpioa.pa7.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 3
    let pa6 = gpioa.pa6.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 4

    init::LateResources {
        PICONODE_LED_TIMER: tim3,
        PICOTALK_RX_LEFT: pa1,
        PICOTALK_RX_TIMER: tim14,

        LED_1_PIN: pa2,
        LED_2_PIN: pa3,
        LED_3_PIN: pa7,
        LED_4_PIN: pa6,
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

        static PICONODE_LED_TIMER: Timer<stm32f0x0::TIM3>;
        static PICOTALK_RX_LEFT: PA1<Output<OpenDrain>>;
        static PICOTALK_RX_STATE: picotalk::RecieveState = picotalk::RecieveState::HandshakeListen(0);
        static PICOTALK_RX_TIMER: Timer<stm32f0x0::TIM14>;
        static VALUE_LEFT: i8 = 0;

        static LED_1_PIN: PA2<Output<PushPull>>;    //PA<Input<PullDown>> for switch
        static LED_2_PIN: PA3<Output<PushPull>>;
        static LED_3_PIN: PA7<Output<PushPull>>;
        static LED_4_PIN: PA6<Output<PushPull>>;
    },
    tasks: {
        TIM14: {
            path: picotalk_rx_tick,     //, PICOTALK_RX_DOWN, PICOTALK_RX_UP, PICOTALK_RX_RIGHT
            resources: [VALUE_LEFT, PICOTALK_RX_LEFT, PICOTALK_RX_STATE, PICOTALK_RX_TIMER],
            priority: 2,
        },
        TIM3: {
            path: piconode_lighting_led,
            resources: [VALUE_LEFT, LED_1_PIN, LED_2_PIN, LED_3_PIN, LED_4_PIN, PICONODE_LED_TIMER],
            priority: 1,
        }
    }
}
