# Makefile for local (non-ESP) execution

.PHONY: all clean

all: netclient

netclient.d:
standalone.d:

include netclient.d standalone.d

CPPFLAGS += -Wall -MD -DSTANDALONE -Iinclude

LDLIBS += -lmbedtls -lmbedcrypto -lmbedx509

netclient: standalone.o netclient.o

clean:
	rm -f *.o
	rm -f *.d
	rm -f netclient
