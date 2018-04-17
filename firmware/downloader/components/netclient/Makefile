# Makefile for local (non-ESP) execution

.PHONY: all clean

all: netclient

include *.d

CPPFLAGS += -Wall -MD

LDLIBS += -lmbedtls -lmbedcrypto -lmbedx509

netclient: main.o netclient.o

clean:
	rm *.o
	rm netclient
