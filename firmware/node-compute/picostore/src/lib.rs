#![no_std]
#![feature(asm)]

extern crate byteorder;
extern crate cortex_m;
extern crate stm32f0x0;

use core::slice;
use core::ops::Deref;

use stm32f0x0::{FLASH, CRC, flash};

use byteorder::{NativeEndian, ByteOrder};

pub const PICOSTORE_BYTES: usize = 60;
const PICOSTORE_HALFWORDS: usize = PICOSTORE_BYTES / 2 + PICOSTORE_BYTES % 2;

// This MUST be linked into its own flash page
#[link_section = ".data.picostore"]
static mut PICOSTORE_CODE: [u16; PICOSTORE_HALFWORDS] = [0; PICOSTORE_HALFWORDS];

#[link_section = ".data.picostore"]
static mut PICOSTORE_CODE_BYTES: u16 = 0;

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
            slice::from_raw_parts(&PICOSTORE_CODE as *const u16 as *const u8, PICOSTORE_CODE_BYTES as usize)
        }
    }

    pub fn crc(&self, crc: &mut CRC) -> u32 {
        crc.cr.write(|w| w.reset().set_bit());
        for byte in self.iter() {
            crc.dr.write(|w| unsafe { w.dr().bits(*byte as u32) });
        }
        crc.dr.read().dr().bits()
    }

    fn unlock(&mut self, flash: &mut FLASH) {
        if flash.cr.read().lock().bit() {
            unsafe {
                flash.keyr.write(|w| w.fkeyr().bits(0x45670123));
                flash.keyr.write(|w| w.fkeyr().bits(0xCDEF89AB));
            }
        }
    }

    fn erase(&mut self, flash: &mut FLASH) {
        wait_while_busy(&flash.sr);
        flash.cr.modify(|_, w| w.per().set_bit());
        flash
            .ar
            .write(|w| unsafe { w.far().bits((&PICOSTORE_CODE[0]) as *const u16 as u32) });
        self.unlock(flash);
        flash.cr.modify(|_, w| w.strt().set_bit());
        wait_while_busy(&flash.sr);

        assert_eq!(flash.sr.read().eop().bit(), true);
        flash.sr.write(|w| w.eop().set_bit());
        flash.cr.modify(|_, w| w.per().clear_bit());
    }

    pub fn replace(&mut self, new: &[u8], flash: &mut FLASH) {
        self.unlock(flash);
        self.erase(flash);

        flash.cr.modify(|_, w| w.pg().set_bit());
        for i in 0..PICOSTORE_HALFWORDS {
            unsafe {
                // volatile operations are only available for pointers, not borrows
                let reg = &mut PICOSTORE_CODE[i] as *mut u16;
                // Would use a range here, but we want to zero-extend...
                reg.write_volatile(NativeEndian::read_u16(&[
                    *new.get(i*2).unwrap_or(&0),
                    *new.get(i*2 + 1).unwrap_or(&0),
                ]));
                wait_while_busy(&flash.sr);
            }

            assert_eq!(flash.sr.read().eop().bit(), true);
            flash.sr.modify(|_, w| w.eop().clear_bit());
        }

        unsafe {
            ((&mut PICOSTORE_CODE_BYTES) as *mut u16).write_volatile(usize::min(PICOSTORE_BYTES, new.len()) as u16);
        }
        assert_eq!(flash.sr.read().eop().bit(), true);
        flash.sr.modify(|_, w| w.eop().clear_bit());

        flash.cr.modify(|_, w| w.pg().clear_bit());
    }
}

impl Deref for PicoStore {
    type Target = [u8];

    fn deref(&self) -> &Self::Target {
        &self.borrow()
    }
}
