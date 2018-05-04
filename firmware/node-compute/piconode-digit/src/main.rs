// -*- mode: rust; flycheck-rust-check-tests: nil; -*-

#![feature(used, proc_macro)]
#![no_std]

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_rtfm as rtfm;
extern crate cortex_m_semihosting;
extern crate stm32f0x0_hal;
extern crate embedded_hal;
extern crate picotalk;

#[cfg(feature = "debug")]
extern crate panic_semihosting;

#[cfg(not(feature = "debug"))]
extern crate panic_abort;

#[macro_use]
extern crate nb;

use rtfm::{app, Threshold};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::gpio::{Output, OpenDrain, PushPull, gpioa::{PA0, PA2, PA3, PA4, PA5, PA6, PA7}, gpiob::PB1};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

fn picotalk_tick(_t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_STATE;
    let mut pin = r.PICOTALK_PIN;
    let mut timer = r.PICOTALK_TIMER;

    timer.wait().unwrap();
    picotalk::transmit_value(&mut *pin, &mut state, 15);
}

fn led_update(_t: &mut Threshold, r: TIM14::Resources) {
    let mut timer = r.DIGIT_TIMER;
    let mut digit_01 = r.DIGIT_01;
    let mut digit_23 = r.DIGIT_23;
    let mut digit_45 = r.DIGIT_45;
    let mut digit_67 = r.DIGIT_67;
    let mut digit_89 = r.DIGIT_89;
    let mut digit_even = r.DIGIT_EVEN;
    let mut digit_odd = r.DIGIT_ODD;
    let mut current = r.DIGIT_CURRENT;

    timer.wait().unwrap();

    digit_01.set_low();
    digit_23.set_low();
    digit_45.set_low();
    digit_67.set_low();
    digit_89.set_low();
    digit_even.set_low();
    digit_odd.set_low();

    if *current < 9 {
        *current += 1;
    } else {
        *current = 0;
    }

    match *current / 2 {
        0 => digit_01.set_high(),
        1 => digit_23.set_high(),
        2 => digit_45.set_high(),
        3 => digit_67.set_high(),
        4 => digit_89.set_high(),
        _ => {},
    };

    match *current % 2 {
        0 => digit_even.set_high(),
        1 => digit_odd.set_high(),
        _ => {},
    };
}

fn init(p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let rcc = p.device.RCC;
    rcc.ahbenr.modify(|_,w| w.crcen().set_bit());

    let mut rcc = rcc.constrain();
    let mut flash = p.device.FLASH.constrain();
    let clocks = rcc.cfgr.freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);
    let mut gpiob = p.device.GPIOB.split(&mut rcc.ahb);

    let pa0 = gpioa.pa0.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    let mut tim3 = Timer::tim3(p.device.TIM3, 1.hz(), clocks, &mut rcc.apb1);
    // tim3.listen(TimerEvent::TimeOut);

    let pa2 = gpioa.pa2.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pa3 = gpioa.pa3.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pa4 = gpioa.pa4.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pa5 = gpioa.pa5.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pa6 = gpioa.pa6.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pa7 = gpioa.pa7.into_push_pull_output(&mut gpioa.moder, &mut gpioa.otyper);
    let pb1 = gpiob.pb1.into_push_pull_output(&mut gpiob.moder, &mut gpiob.otyper);
    let mut tim14 = Timer::tim14(p.device.TIM14, 1.hz(), clocks, &mut rcc.apb1);
    tim14.listen(TimerEvent::TimeOut);

    init::LateResources {
        PICOTALK_PIN: pa0,
        PICOTALK_TIMER: tim3,

        DIGIT_01: pb1,
        DIGIT_23: pa2,
        DIGIT_45: pa3,
        DIGIT_67: pa6,
        DIGIT_89: pa7,
        DIGIT_EVEN: pa4,
        DIGIT_ODD: pa5,
        DIGIT_TIMER: tim14,
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
        static PICOTALK_PIN: PA0<Output<OpenDrain>>;
        static PICOTALK_STATE: picotalk::TransmitState = picotalk::TransmitState::HandshakeAdvertise(0);
        static PICOTALK_TIMER: Timer<stm32f0x0::TIM3>;

        static DIGIT_TIMER: Timer<stm32f0x0::TIM14>;

        static DIGIT_01: PB1<Output<PushPull>>;
        static DIGIT_23: PA2<Output<PushPull>>;
        static DIGIT_45: PA3<Output<PushPull>>;
        static DIGIT_67: PA6<Output<PushPull>>;
        static DIGIT_89: PA7<Output<PushPull>>;

        static DIGIT_EVEN: PA4<Output<PushPull>>;
        static DIGIT_ODD: PA5<Output<PushPull>>;

        static DIGIT_CURRENT: u8 = 0;
    },
    tasks: {
        TIM3: {
            path: picotalk_tick,
            resources: [PICOTALK_PIN, PICOTALK_STATE, PICOTALK_TIMER],
            priority: 1,
        },

        TIM14: {
            path: led_update,
            resources: [
                DIGIT_TIMER,
                DIGIT_01,
                DIGIT_23,
                DIGIT_45,
                DIGIT_67,
                DIGIT_89,
                DIGIT_EVEN,
                DIGIT_ODD,
                DIGIT_CURRENT,
            ],
            priority: 1,
        },
    }
}
