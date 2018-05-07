#!/usr/bin/env python3

import sys

file_name_in = sys.argv[1]
file_name_out = sys.argv[2]
symbol_name = sys.argv[3]

file_in = open(file_name_in, "r")
file_out = open(file_name_out, "w")

active = False
for line in file_in:
    parts = line.split(" ")
    if parts[0] == "DEF":
        active = parts[1] == symbol_name
    if parts[0] == "X" and active:
        pin_number = int(parts[2])
        if pin_number % 2 == 0:
            parts[2] = pin_number - 1
        else:
            parts[2] = pin_number + 1
    print(*parts, end='', file=file_out)

file_in.close()
file_out.close()
