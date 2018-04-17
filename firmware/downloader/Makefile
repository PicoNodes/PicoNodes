#
# This is a project Makefile. It is assumed the directory this Makefile resides in is a
# project subdirectory.
#

PROJECT_NAME := piconodes-downloader
MKSPIFFS := vendor/mkspiffs/mkspiffs

DATA_BIN_SIZE_BYTES = 65536 # 1024B * 64 = 64KiB
DATA_BIN_OFFSET = 0x110000

ESPTOOL_ALL_FLASH_ARGS += $(DATA_BIN_OFFSET) data.bin

include $(IDF_PATH)/make/project.mk

data.bin: data-src/*
	$(MKSPIFFS) --create data-src --block 4096 --page 256 --size $(DATA_BIN_SIZE_BYTES) $@

data-flash: data.bin $(ESPTOOLPY_SRC)
	$(ESPTOOLPY_WRITE_FLASH) $(DATA_BIN_OFFSET) data.bin

all-binaries: data.bin

.PHONY: data-flash
