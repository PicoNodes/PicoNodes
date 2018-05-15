// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
#![no_std]

extern crate cortex_m;          //Low level access to the cortex-m processor
extern crate cortex_m_rt;       //Runtime for cortex-m microcontrollers
extern crate cortex_m_rtfm as rtfm;   //Real Time For the Masses framework for thhe ARM-cortex
extern crate stm32f0x0_hal;     //HAL for the stm32f0x0 family. Implementation of the embedded hal traits
extern crate embedded_hal;      //Hardware abstraction layer for embedded systems
extern crate picotalk;      //Enables communication between the nodes

#[cfg(feature = "debug")]
extern crate panic_semihosting;
#[cfg(feature = "debug")]
extern crate cortex_m_semihosting;  //Enables coderunning on an ARM-target to use input/output pins

#[cfg(not(feature = "debug"))]
extern crate panic_abort;

extern crate nb;

#[cfg(feature = "debug")]
use cortex_m_semihosting::hio;

use rtfm::{app, Threshold, Resource};
#[allow(unused)]
use cortex_m::asm;

use embedded_hal::timer::CountDown;
use embedded_hal::digital::InputPin;
use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::gpio::{Output, PushPull, OpenDrain, gpioa::*};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

type Led1 = PA2<Output<PushPull>>;
type Led2 = PA3<Output<PushPull>>;
type Led3 = PA7<Output<PushPull>>;
type Led4 = PA6<Output<PushPull>>;

fn update_leds(value: i8, led_1: &mut Led1, led_2: &mut Led2, led_3: &mut Led3, led_4: &mut Led4) {
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

//Recieving from four directions need four timers tim14, tim3, tim16, tim17
fn picotalk_rx_tick(_t: &mut Threshold, mut r: TIM14::Resources) {
    let mut state = r.PICOTALK_RX_STATE;
    let mut pin = r.PICOTALK_RX_LEFT;
    let mut timer = r.PICOTALK_RX_TIMER;
    let mut value = r.VALUE_LEFT;
    let exti = r.EXTI;

    let _ = timer.wait();
    picotalk::recieve_value(&mut *pin, &mut *state);
    if let picotalk::RecieveState::Done(new_value) = *state {
        *value = new_value;
        *state = picotalk::RecieveState::HandshakeListen(0);
        update_leds(
            *value,
            &mut *r.LED_1_PIN,
            &mut *r.LED_2_PIN,
            &mut *r.LED_3_PIN,
            &mut *r.LED_4_PIN
        );

        // Disable interrupt, and re-enable picotalk_rx_start
        timer.unlisten(TimerEvent::TimeOut);
        exti.imr.modify(|_, w| w.mr1().set_bit());
    }
    assert_eq!(timer.wait(), Err(nb::Error::WouldBlock));
}

fn picotalk_rx_start(t: &mut Threshold, r: EXTI0_1::Resources) {
    let mut timer = r.PICOTALK_RX_TIMER;
    let mut exti = r.EXTI;
    let mut nvic = r.NVIC;

    exti.claim_mut(t, |exti, _t| {
        // Disable interrupt for the rest of the message
        exti.imr.modify(|_, w| w.mr1().clear_bit());
        exti.pr.modify(|_, w| w.pr1().set_bit());
    });
    timer.claim_mut(t, |timer, _t| {
        timer.start(picotalk::PICOTALK_FREQ);
        timer.listen(TimerEvent::TimeOut);
    });
    nvic.set_pending(stm32f0x0::Interrupt::TIM14);
}

//Lighting the LED after recieving the recieve_value
// fn piconode_lighting_led(t: &mut Threshold, r: TIM3::Resources) {
//     let mut led_1 = r.LED_1_PIN;
//     let mut led_2 = r.LED_2_PIN;
//     let mut led_3 = r.LED_3_PIN;
//     let mut led_4 = r.LED_4_PIN;
//     let value = r.VALUE_LEFT;
//     let mut timer = r.PICONODE_LED_TIMER;

//     timer.wait().unwrap();
//     let value = value.claim(t, |value, _t| *value);
//     let mut out = hio::hstdout().unwrap();
//     writeln!(out, "Turning on: {}", value).unwrap();

//     update_leds(value, &mut *led_1, &mut *led_2, &mut *led_3, &mut *led_4);
// }

fn init(p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let exti = p.device.EXTI;
    let syscfg = p.device.SYSCFG;
    let mut rcc = p.device.RCC.constrain();
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
    while InputPin::is_high(&pa1) {}

    // let mut tim3 = Timer::tim3(p.device.TIM3, 10.hz(), clocks, &mut rcc.apb1);
    let tim14 = Timer::tim14(p.device.TIM14, picotalk::PICOTALK_FREQ, clocks, &mut rcc.apb1);
    // tim3.listen(TimerEvent::TimeOut);
    // tim14.listen(TimerEvent::TimeOut);

    // Enable falling edge trigger on PA1
    syscfg.exticr1.modify(|_, w| unsafe { w.exti1().bits(0b0000) });
    exti.ftsr.modify(|_, w| w.tr1().set_bit());
    exti.imr.modify(|_, w| w.mr1().set_bit());

    let pa2 = gpioa.pa2.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 1
    let pa3 = gpioa.pa3.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 2
    let pa7 = gpioa.pa7.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 3
    let pa6 = gpioa.pa6.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper); //led 4

    init::LateResources {
        // PICONODE_LED_TIMER: tim3,
        PICOTALK_RX_LEFT: pa1,
        PICOTALK_RX_TIMER: tim14,

        LED_1_PIN: pa2,
        LED_2_PIN: pa3,
        LED_3_PIN: pa7,
        LED_4_PIN: pa6,

        EXTI: exti,
        NVIC: p.core.NVIC,
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
        // static PICONODE_LED_TIMER: Timer<stm32f0x0::TIM3>;
        static PICOTALK_RX_LEFT: PA1<Output<OpenDrain>>;
        static PICOTALK_RX_STATE: picotalk::RecieveState = picotalk::RecieveState::HandshakeListen(0);
        static PICOTALK_RX_TIMER: Timer<stm32f0x0::TIM14>;
        static VALUE_LEFT: i8 = 0;

        static LED_1_PIN: Led1;    //PA<Input<PullDown>> for switch
        static LED_2_PIN: Led2;
        static LED_3_PIN: Led3;
        static LED_4_PIN: Led4;

        static EXTI: stm32f0x0::EXTI;
        static NVIC: stm32f0x0::NVIC;
    },
    tasks: {
        TIM14: {
            path: picotalk_rx_tick,     //, PICOTALK_RX_DOWN, PICOTALK_RX_UP, PICOTALK_RX_RIGHT
            resources: [VALUE_LEFT, PICOTALK_RX_LEFT, PICOTALK_RX_STATE, PICOTALK_RX_TIMER, LED_1_PIN, LED_2_PIN, LED_3_PIN, LED_4_PIN, EXTI],
            priority: 2,
        },
        EXTI0_1: {
            path: picotalk_rx_start,
            resources: [PICOTALK_RX_TIMER, EXTI, NVIC],
            priority: 1,
        },
        // TIM3: {
        //     path: piconode_lighting_led,
        //     resources: [VALUE_LEFT, LED_1_PIN, LED_2_PIN, LED_3_PIN, LED_4_PIN, PICONODE_LED_TIMER],
        //     priority: 1,
        // }
    }
}
