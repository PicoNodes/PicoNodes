#![no_std]

extern crate stm32f0x0;

use stm32f0x0::flash;

// This MUST MATCH the page that PICOSTORE_CODE is placed in
const PICOSTORE_PAGE: u32 = 15;

#[link_section = ".data.picostore"]
static mut PICOSTORE_CODE: [u8; 60] = [0; 60];

static mut TAKEN: bool = false;

pub struct PicoStore;

fn wait_while_busy(sr: &flash::SR) {
    while sr.read().bsy().bit() {}
}

impl PicoStore {
    fn take() -> Option<PicoStore> {
        unsafe {
            if TAKEN {
                None
            } else {
                TAKEN = true;
                Some(PicoStore)
            }
        }
    }

    fn borrow(&self) -> &[u8] {
        unsafe {
            &PICOSTORE_CODE
        }
    }

    fn erase(&mut self, flash: &mut flash::RegisterBlock) {
        wait_while_busy(&flash.sr);
        flash.cr.modify(|_,w| w.per().set_bit());
        flash.ar.write(|w| unsafe { w.far().bits(PICOSTORE_PAGE) });
        flash.cr.modify(|_,w| w.strt().set_bit());
        wait_while_busy(&flash.sr);

        assert_eq!(flash.sr.read().eop().bit(), true);
        flash.sr.modify(|_,w| w.eop().clear_bit());
    }

    fn replace(&mut self, new: &[u8], flash: &mut flash::RegisterBlock) {
        self.erase(flash);

        unimplemented!()
    }
}
