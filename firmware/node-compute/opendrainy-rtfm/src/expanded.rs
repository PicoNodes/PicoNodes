#![feature(prelude_import)]
#![no_std]
// -*- mode: rust; flycheck-rust-check-tests: nil; -*-
#![feature(used, proc_macro)]
#![no_std]
#[prelude_import]
use core::prelude::v1::*;
#[macro_use]
extern crate core;
#[macro_use]
extern crate compiler_builtins;

extern crate cortex_m;
extern crate cortex_m_rt;
extern crate cortex_m_rtfm as rtfm;
extern crate cortex_m_semihosting;
extern crate embedded_hal;
extern crate panic_semihosting;
extern crate stm32f0x0_hal;

use core::fmt::Write;
use cortex_m_semihosting::hio;

use embedded_hal::digital::*;

use cortex_m::asm;
use rtfm::{app, Threshold};

use stm32f0x0_hal::delay::Delay;
use stm32f0x0_hal::gpio::{gpioa::PA4, OpenDrain, Output};
use stm32f0x0_hal::prelude::*;
use stm32f0x0_hal::serial::{Event as SerialEvent, Rx, Serial, Tx};
use stm32f0x0_hal::stm32f0x0::{self, CorePeripherals, Peripherals};
use stm32f0x0_hal::time::Hertz;

fn loopback(_t: &mut Threshold, r: USART1::Resources) {
    let mut rx = r.SERIAL1_RX;
    let mut tx = r.SERIAL1_TX;

    tx.write(rx.read().unwrap()).unwrap();
}

fn blink(_t: &mut Threshold, mut r: TIM3::Resources) {
    let mut state = r.BLINKY_STATE;
    *state = !*state;
    if *state {
        r.BLINKY_PIN.set_high();
    } else {
        r.BLINKY_PIN.set_low();
    }
}

fn systick() {
    // rtfm::bkpt();
}

fn init(mut p: init::Peripherals, _r: init::Resources) -> init::LateResources {
    let mut rcc = p.device.RCC.constrain();
    let mut flash = p.device.FLASH.constrain();
    let clocks = rcc.cfgr.freeze(&mut flash.acr);
    let mut gpioa = p.device.GPIOA.split(&mut rcc.ahb);

    let pa2 = gpioa.pa2.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa3 = gpioa.pa3.into_af1(&mut gpioa.moder, &mut gpioa.afrl);
    let pa4 = gpioa
        .pa4
        .into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);

    let usart1 = p.device.USART1;
    let mut serial = Serial::usart1(usart1, (pa2, pa3), 115200.bps(), clocks, &mut rcc.apb2);
    serial.listen(SerialEvent::Rxne);
    let (tx, rx) = serial.split();

    p.core
        .SYST
        .set_clock_source(cortex_m::peripheral::syst::SystClkSource::Core);
    p.core.SYST.set_reload(8000000);
    p.core.SYST.enable_interrupt();
    p.core.SYST.enable_counter();

    init::LateResources {
        SERIAL1_RX: rx,
        SERIAL1_TX: tx,
        BLINKY_PIN: pa4,
    }
}

fn idle() -> ! {
    loop {
        // rtfm::wfi();
        asm::nop();
    }
}

// fn main() {
//     let mut out = hio::hstdout().unwrap();
//     let peripherals = Peripherals::take().unwrap();
//     let core_peripherals = CorePeripherals::take().unwrap();
//     let mut flash = peripherals.FLASH.constrain();
//     let mut rcc = peripherals.RCC.constrain();
//     let clocks = rcc.cfgr.freeze(&mut flash.acr);

//     let mut gpioa = peripherals.GPIOA.split(&mut rcc.ahb);
//     let mut delay = Delay::new(core_peripherals.SYST, clocks);

//     let mut pa4 = gpioa.pa4.into_open_drain_output(&mut gpioa.moder, &mut gpioa.otyper);

//     let mut state = true;
//     loop {
//         state = !state;
//         if state {
//             pa4.set_high();
//         } else {
//             pa4.set_low();
//         }
//         writeln!(out, "O: {}, I: {}", OutputPin::is_high(&pa4), InputPin::is_high(&pa4)).unwrap();

//         delay.delay_ms(500u16);
//     }
// }

#[allow(unsafe_code)]
unsafe impl rtfm::Resource for TIM3::BLINKY_STATE {
    type Data = bool;
    fn borrow<'cs>(&'cs self, t: &'cs Threshold) -> &'cs Self::Data {
        if !(t.value() >= 1u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 1u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &_BLINKY_STATE }
    }
    fn borrow_mut<'cs>(&'cs mut self, t: &'cs Threshold) -> &'cs mut Self::Data {
        if !(t.value() >= 1u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 1u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &mut _BLINKY_STATE }
    }
    fn claim<R, F>(&self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&_BLINKY_STATE, 1u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
    fn claim_mut<R, F>(&mut self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&mut Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&mut _BLINKY_STATE, 1u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
}
#[allow(unsafe_code)]
impl core::ops::Deref for TIM3::BLINKY_STATE {
    type Target = bool;
    fn deref(&self) -> &Self::Target {
        unsafe { &_BLINKY_STATE }
    }
}
#[allow(unsafe_code)]
impl core::ops::DerefMut for TIM3::BLINKY_STATE {
    fn deref_mut(&mut self) -> &mut Self::Target {
        unsafe { &mut _BLINKY_STATE }
    }
}
#[allow(unsafe_code)]
unsafe impl rtfm::Resource for TIM3::BLINKY_PIN {
    type Data = PA4<Output<OpenDrain>>;
    fn borrow<'cs>(&'cs self, t: &'cs Threshold) -> &'cs Self::Data {
        if !(t.value() >= 1u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 1u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &_BLINKY_PIN.some }
    }
    fn borrow_mut<'cs>(&'cs mut self, t: &'cs Threshold) -> &'cs mut Self::Data {
        if !(t.value() >= 1u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 1u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &mut _BLINKY_PIN.some }
    }
    fn claim<R, F>(&self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&_BLINKY_PIN.some, 1u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
    fn claim_mut<R, F>(&mut self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&mut Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&mut _BLINKY_PIN.some, 1u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
}
#[allow(unsafe_code)]
impl core::ops::Deref for TIM3::BLINKY_PIN {
    type Target = PA4<Output<OpenDrain>>;
    fn deref(&self) -> &Self::Target {
        unsafe { &_BLINKY_PIN.some }
    }
}
#[allow(unsafe_code)]
impl core::ops::DerefMut for TIM3::BLINKY_PIN {
    fn deref_mut(&mut self) -> &mut Self::Target {
        unsafe { &mut _BLINKY_PIN.some }
    }
}
#[allow(non_snake_case)]
#[allow(unsafe_code)]
#[export_name = "TIM3"]
pub unsafe extern "C" fn _TIM3() {
    let f: fn(&mut rtfm::Threshold, TIM3::Resources) = blink;
    f(
        &mut if 1u8 == 1 << stm32f0x0::NVIC_PRIO_BITS {
            rtfm::Threshold::new(::core::u8::MAX)
        } else {
            rtfm::Threshold::new(1u8)
        },
        TIM3::Resources::new(),
    )
}
#[allow(non_snake_case)]
#[allow(unsafe_code)]
mod TIM3 {
    #[allow(unused_imports)]
    use core::marker::PhantomData;
    #[allow(dead_code)]
    #[deny(const_err)]
    pub const CHECK_PRIORITY: (u8, u8) = (1u8 - 1, (1 << ::stm32f0x0::NVIC_PRIO_BITS) - 1u8);
    #[allow(non_camel_case_types)]
    pub struct BLINKY_STATE {
        _0: PhantomData<*const ()>,
    }
    #[allow(non_camel_case_types)]
    pub struct BLINKY_PIN {
        _0: PhantomData<*const ()>,
    }
    #[allow(non_snake_case)]
    pub struct Resources {
        pub BLINKY_STATE: BLINKY_STATE,
        pub BLINKY_PIN: BLINKY_PIN,
    }
    #[allow(unsafe_code)]
    impl Resources {
        pub unsafe fn new() -> Self {
            Resources {
                BLINKY_STATE: BLINKY_STATE { _0: PhantomData },
                BLINKY_PIN: BLINKY_PIN { _0: PhantomData },
            }
        }
    }
}
#[allow(unsafe_code)]
unsafe impl rtfm::Resource for USART1::SERIAL1_TX {
    type Data = Tx<stm32f0x0::USART1>;
    fn borrow<'cs>(&'cs self, t: &'cs Threshold) -> &'cs Self::Data {
        if !(t.value() >= 2u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 2u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &_SERIAL1_TX.some }
    }
    fn borrow_mut<'cs>(&'cs mut self, t: &'cs Threshold) -> &'cs mut Self::Data {
        if !(t.value() >= 2u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 2u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &mut _SERIAL1_TX.some }
    }
    fn claim<R, F>(&self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&_SERIAL1_TX.some, 2u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
    fn claim_mut<R, F>(&mut self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&mut Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&mut _SERIAL1_TX.some, 2u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
}
#[allow(unsafe_code)]
impl core::ops::Deref for USART1::SERIAL1_TX {
    type Target = Tx<stm32f0x0::USART1>;
    fn deref(&self) -> &Self::Target {
        unsafe { &_SERIAL1_TX.some }
    }
}
#[allow(unsafe_code)]
impl core::ops::DerefMut for USART1::SERIAL1_TX {
    fn deref_mut(&mut self) -> &mut Self::Target {
        unsafe { &mut _SERIAL1_TX.some }
    }
}
#[allow(unsafe_code)]
unsafe impl rtfm::Resource for USART1::SERIAL1_RX {
    type Data = Rx<stm32f0x0::USART1>;
    fn borrow<'cs>(&'cs self, t: &'cs Threshold) -> &'cs Self::Data {
        if !(t.value() >= 2u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 2u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &_SERIAL1_RX.some }
    }
    fn borrow_mut<'cs>(&'cs mut self, t: &'cs Threshold) -> &'cs mut Self::Data {
        if !(t.value() >= 2u8) {
            {
                ::panicking::panic(&(
                    "assertion failed: t.value() >= 2u8",
                    "src/main.rs",
                    111u32,
                    1u32,
                ))
            }
        };
        unsafe { &mut _SERIAL1_RX.some }
    }
    fn claim<R, F>(&self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&_SERIAL1_RX.some, 2u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
    fn claim_mut<R, F>(&mut self, t: &mut Threshold, f: F) -> R
    where
        F: FnOnce(&mut Self::Data, &mut Threshold) -> R,
    {
        unsafe { rtfm::claim(&mut _SERIAL1_RX.some, 2u8, stm32f0x0::NVIC_PRIO_BITS, t, f) }
    }
}
#[allow(unsafe_code)]
impl core::ops::Deref for USART1::SERIAL1_RX {
    type Target = Rx<stm32f0x0::USART1>;
    fn deref(&self) -> &Self::Target {
        unsafe { &_SERIAL1_RX.some }
    }
}
#[allow(unsafe_code)]
impl core::ops::DerefMut for USART1::SERIAL1_RX {
    fn deref_mut(&mut self) -> &mut Self::Target {
        unsafe { &mut _SERIAL1_RX.some }
    }
}
#[allow(non_snake_case)]
#[allow(unsafe_code)]
#[export_name = "USART1"]
pub unsafe extern "C" fn _USART1() {
    let f: fn(&mut rtfm::Threshold, USART1::Resources) = loopback;
    f(
        &mut if 2u8 == 1 << stm32f0x0::NVIC_PRIO_BITS {
            rtfm::Threshold::new(::core::u8::MAX)
        } else {
            rtfm::Threshold::new(2u8)
        },
        USART1::Resources::new(),
    )
}
#[allow(non_snake_case)]
#[allow(unsafe_code)]
mod USART1 {
    #[allow(unused_imports)]
    use core::marker::PhantomData;
    #[allow(dead_code)]
    #[deny(const_err)]
    pub const CHECK_PRIORITY: (u8, u8) = (2u8 - 1, (1 << ::stm32f0x0::NVIC_PRIO_BITS) - 2u8);
    #[allow(non_camel_case_types)]
    pub struct SERIAL1_TX {
        _0: PhantomData<*const ()>,
    }
    #[allow(non_camel_case_types)]
    pub struct SERIAL1_RX {
        _0: PhantomData<*const ()>,
    }
    #[allow(non_snake_case)]
    pub struct Resources {
        pub SERIAL1_TX: SERIAL1_TX,
        pub SERIAL1_RX: SERIAL1_RX,
    }
    #[allow(unsafe_code)]
    impl Resources {
        pub unsafe fn new() -> Self {
            Resources {
                SERIAL1_TX: SERIAL1_TX { _0: PhantomData },
                SERIAL1_RX: SERIAL1_RX { _0: PhantomData },
            }
        }
    }
}
#[allow(non_camel_case_types)]
#[allow(non_snake_case)]
pub struct _initResources<'a> {
    pub BLINKY_STATE: &'a mut bool,
}
#[allow(non_camel_case_types)]
#[allow(non_snake_case)]
pub struct _initLateResources {
    pub SERIAL1_TX: Tx<stm32f0x0::USART1>,
    pub SERIAL1_RX: Rx<stm32f0x0::USART1>,
    pub BLINKY_PIN: PA4<Output<OpenDrain>>,
}
#[allow(unsafe_code)]
mod init {
    pub struct Peripherals {
        pub core: ::stm32f0x0::CorePeripherals,
        pub device: ::stm32f0x0::Peripherals,
    }
    pub use _initResources as Resources;
    #[allow(unsafe_code)]
    impl<'a> Resources<'a> {
        pub unsafe fn new() -> Self {
            Resources {
                BLINKY_STATE: &mut ::_BLINKY_STATE,
            }
        }
    }
    pub use _initLateResources as LateResources;
}
static mut _BLINKY_PIN: rtfm::UntaggedOption<PA4<Output<OpenDrain>>> =
    rtfm::UntaggedOption { none: () };
static mut _SERIAL1_TX: rtfm::UntaggedOption<Tx<stm32f0x0::USART1>> =
    rtfm::UntaggedOption { none: () };
static mut _SERIAL1_RX: rtfm::UntaggedOption<Rx<stm32f0x0::USART1>> =
    rtfm::UntaggedOption { none: () };
static mut _BLINKY_STATE: bool = false;
#[allow(unsafe_code)]
fn main() {
    #![allow(path_statements)]
    TIM3::CHECK_PRIORITY;
    USART1::CHECK_PRIORITY;
    let init: fn(init::Peripherals, init::Resources) -> ::init::LateResources = init;
    rtfm::atomic(unsafe { &mut rtfm::Threshold::new(0) }, |_t| unsafe {
        let _late_resources = init(
            init::Peripherals {
                core: ::stm32f0x0::CorePeripherals::steal(),
                device: ::stm32f0x0::Peripherals::steal(),
            },
            init::Resources::new(),
        );
        _SERIAL1_TX = rtfm::UntaggedOption {
            some: _late_resources.SERIAL1_TX,
        };
        _SERIAL1_RX = rtfm::UntaggedOption {
            some: _late_resources.SERIAL1_RX,
        };
        _BLINKY_PIN = rtfm::UntaggedOption {
            some: _late_resources.BLINKY_PIN,
        };
        use stm32f0x0::Interrupt;
        let mut nvic: stm32f0x0::NVIC = core::mem::transmute(());
        let prio_bits = stm32f0x0::NVIC_PRIO_BITS;
        let hw = ((1 << prio_bits) - 1u8) << (8 - prio_bits);
        nvic.set_priority(Interrupt::TIM3, hw);
        nvic.enable(Interrupt::TIM3);
        let prio_bits = stm32f0x0::NVIC_PRIO_BITS;
        let hw = ((1 << prio_bits) - 2u8) << (8 - prio_bits);
        nvic.set_priority(Interrupt::USART1, hw);
        nvic.enable(Interrupt::USART1);
    });
    let idle: fn() -> ! = idle;
    idle();
}
