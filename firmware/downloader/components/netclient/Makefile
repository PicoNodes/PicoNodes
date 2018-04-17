# Makefile for local (non-ESP) execution

.PHONY: all clean

all: netclient

include *.d

CPPFLAGS += -Wall -MD -DSTANDALONE

LDLIBS += -lmbedtls -lmbedcrypto -lmbedx509

netclient: standalone.o netclient.o

clean:
	rm *.o
	rm netclient
