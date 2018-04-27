#![no_std]
#![feature(asm)]

extern crate stm32f0x0;
extern crate cortex_m;
extern crate cortex_m_semihosting;

use core::fmt::Write;
use core::mem::transmute;
use cortex_m_semihosting::hio;

use cortex_m::asm;
use stm32f0x0::flash;

// This MUST MATCH the page that PICOSTORE_CODE is placed in
const PICOSTORE_PAGE: u32 = 15;
const PICOSTORE_BYTES: usize = 60;
const PICOSTORE_HALFWORDS: usize = PICOSTORE_BYTES / 2 + PICOSTORE_BYTES % 2;

#[link_section = ".data.picostore"]
static mut PICOSTORE_CODE: [u8; PICOSTORE_BYTES] = [0; PICOSTORE_BYTES];

static mut TAKEN: bool = false;

pub struct PicoStore;

fn wait_while_busy(sr: &flash::SR) {
    while sr.read().bsy().bit() {}
}

impl PicoStore {
    pub fn take() -> Option<PicoStore> {
        unsafe {
            if TAKEN {
                None
            } else {
                TAKEN = true;
                Some(PicoStore)
            }
        }
    }

    pub fn borrow(&self) -> &[u8] {
        unsafe {
            &PICOSTORE_CODE
        }
    }

    fn unlock(&mut self, flash: &mut flash::RegisterBlock) {
        if flash.cr.read().lock().bit() {
            unsafe {
                flash.keyr.write(|w| w.fkeyr().bits(0x45670123));
                flash.keyr.write(|w| w.fkeyr().bits(0xCDEF89AB));
            }
        }
    }

    fn erase(&mut self, flash: &mut flash::RegisterBlock) {
        wait_while_busy(&flash.sr);
        flash.cr.write(|w| w.per().set_bit());
        flash.ar.write(|w| unsafe { w.far().bits(PICOSTORE_PAGE) });
        self.unlock(flash);
        flash.cr.modify(|_,w| w.strt().set_bit());
        wait_while_busy(&flash.sr);

        assert_eq!(flash.sr.read().eop().bit(), true);
        flash.sr.write(|w| w.eop().set_bit());
        flash.cr.write(|w| w.per().clear_bit());
    }

    pub fn replace(&mut self, new: &[u8], flash: &mut flash::RegisterBlock) {
        let mut out = hio::hstdout().unwrap();
        self.unlock(flash);
        self.erase(flash);

        writeln!(out, "SR: {:X}, CR: {:X}",
                 flash.sr.read().bits(),
                 flash.cr.read().bits()).unwrap();
        self.unlock(flash);
        flash.cr.modify(|_,w| w.pg().set_bit());
        writeln!(out, "SR: {:X}, CR: {:X}",
                 flash.sr.read().bits(),
                 flash.cr.read().bits()).unwrap();
        // asm::bkpt();
        unsafe {
            let code_halfwords = transmute::<&mut [u8; PICOSTORE_BYTES], &mut [u16; PICOSTORE_HALFWORDS]>(&mut PICOSTORE_CODE);
            for i in 0..PICOSTORE_BYTES {
                writeln!(out, "{:X} {:X}", new[i], &PICOSTORE_CODE[i]).unwrap();
                if i % 2 == 0 {
                    code_halfwords[i/2] = (new[i] as u16);
                } else {
                    code_halfwords[i/2] = ((new[i] as u16) << 8);
                }
                wait_while_busy(&flash.sr);
                writeln!(out, "{:X} {:X}", new[i], &PICOSTORE_CODE[i]).unwrap();

                assert_eq!(flash.sr.read().eop().bit(), true);
                flash.sr.modify(|_,w| w.eop().clear_bit());
            }
        }
        asm::bkpt();
        flash.cr.modify(|_,w| w.pg().clear_bit());
    }
}
