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

use rtfm::{app, Threshold};
#[allow(unused)]
use cortex_m::asm;

use stm32f0x0_hal::prelude::*;      //Black magic
use stm32f0x0_hal::stm32f0x0;
use stm32f0x0_hal::gpio::{Output, Input, PullDown, OpenDrain, gpioa::*};
use stm32f0x0_hal::timer::{Timer, Event as TimerEvent};

type Switch1 = PA2<Input<PullDown>>;
type Switch2 = PA3<Input<PullDown>>;
type Switch3 = PA7<Input<PullDown>>;
type Switch4 = PA6<Input<PullDown>>;

fn check_switches(switch_1: &Switch1, switch_2: &Switch2, switch_3: &Switch3, switch_4: &Switch4) -> i8 {
    if switch_1.is_high() {
        1
    } else if switch_2.is_high() {
        2
    } else if switch_3.is_high() {
        3
    } else if switch_4.is_high() {
        4
    } else {
        0
    }
}

fn picotalk_tx_tick(_t: &mut Threshold, r: TIM3::Resources) {
    let mut state = r.PICOTALK_TX_STATE;
    let mut pin = r.PICOTALK_TX_RIGHT;
    let mut timer = r.PICOTALK_TX_TIMER;
    let mut value = r.VALUE;

    timer.wait().unwrap();

    picotalk::transmit_value(&mut *pin, &mut state, *value);
    if *state == picotalk::TransmitState::Idle {
        *value = check_switches(
            &*r.SWITCH_1_PIN,
            &*r.SWITCH_2_PIN,
            &*r.SWITCH_3_PIN,
            &*r.SWITCH_4_PIN
        );
        *state = picotalk::TransmitState::HandshakeAdvertise(0);
    }
    assert_eq!(timer.wait(), Err(nb::Error::WouldBlock));
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

    let mut pa5 = gpioa.pa5.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);
    pa5.set_high();

    let mut tim3 = Timer::tim3(p.device.TIM3, picotalk::PICOTALK_FREQ, clocks, &mut rcc.apb1);     //timer for transmitting value
    tim3.listen(TimerEvent::TimeOut);

    let pa2 = gpioa.pa2.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 1
    let pa3 = gpioa.pa3.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 2
    let pa7 = gpioa.pa7.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 3
    let pa6 = gpioa.pa6.into_pull_down_input(&mut gpioa.moder, &mut gpioa.pupdr); //switch 4

    init::LateResources {
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

        static VALUE: i8 = 0;

        static SWITCH_1_PIN: Switch1;
        static SWITCH_2_PIN: Switch2;
        static SWITCH_3_PIN: Switch3;
        static SWITCH_4_PIN: Switch4;
    },
    tasks: {
        TIM3: {
            path: picotalk_tx_tick,
            resources: [VALUE, PICOTALK_TX_RIGHT, PICOTALK_TX_STATE, PICOTALK_TX_TIMER, SWITCH_1_PIN, SWITCH_2_PIN, SWITCH_3_PIN, SWITCH_4_PIN],
            priority: 2,
        },
    }
}
