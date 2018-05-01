SPACING = 2.54;

module sheet() {
    translate([-20, -1, 0]) {
        translate([0, 0, 5]) {
            cube([40, 2, 50]);
        }
        translate([5, 0, 0]) {
            cube([5, 2, 10]);
        }
        translate([21, 0, 0]) {
            cube([5, 2, 10]);
        }
    }
}

module sheet_even() {
    sheet();
}

module sheet_odd() {
    rotate([0, 0, 180]) {
        sheet();
    }
}

module stack(sheets) {
    for (i = [0 : sheets - 1]) {
        translate([0, i * SPACING, 0]) {
            if (i % 2 == 0) {
                sheet_even();
            } else {
                sheet_odd();
            }
        }
    }
}

difference() {
    translate([-22, -3, 3]) {
        cube([44, 40, 8]);
    }
    translate([0, 0, 1.5]) {
        stack(10);
    }
}

color("green") {
    translate([-1, -3, 1]) {
        cube([2, 2 + 10 * SPACING, 3]);
    }
    
    translate([-22, -3, 1]) {
        cube([44, 2, 3]);
        translate([0, 0, -1]) {
            cube([44, 1, 2]);
        }
        
        for (i = [1 : 10]) {
            translate([0, SPACING * i + 1, 0]) {
                cube([44, 1, 3]);
            }
        }
    }
    
    for (i = [0 : 1]) {
        mirror([i, 0, 0]) {
            translate([-22, -3, 1]) {
                cube([2, 40, 3]);
                translate([0, 0, -1]) {
                    cube([1, 40, 2]);
                }
            }
        }
    }
}