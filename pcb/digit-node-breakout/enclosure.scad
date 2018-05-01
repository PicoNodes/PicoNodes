use <../vendor/ClothBotCreations/utilities/fillet.scad>;

SPACING = 2.54;

module sheet() {
    translate([-20, -1, 0]) {
        translate([0, 0, 5]) {
            cube([40, 2, 50]);
        }
        translate([21, 0, 0]) {
            cube([7, 2, 10]);
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

/*translate([0, 0, 0]) {
    rotate([90, 0, 270]) {
        #import("digit-node.stl");
    }
}*/

/*translate([0, 0, 3]) {
    rotate([270, 180, 90]) {
        import("digit-node.stl");
    }
}*/

difference() {
    translate([-22, -4, 3]) {
        cube([44, 55, 8]);
    }
    translate([0, 0, 1.5]) {
        stack(10);
    }
    
    translate([8.5, 7, 2]) {
        cube([3.5, 14, 3.5]);
    }
    
    translate([-11.9, 7, 2]) {
        cube([3.5, 24, 3.5]);
    }
    
    translate([-15, 25, 2]) {
        cube([27.5, 25, 4]);
    }
}

/*color("green") {
    translate([-1, -3, 1]) {
        cube([2, 2 + 10 * SPACING, 3]);
    }
    
    translate([-22, -3, 0.5]) {
        cube([44, 2, 3]);
        
        for (i = [1 : 9]) {
            translate([0, SPACING * i + 1, 0]) {
                cube([44, 1, 3]);
            }
        }
        
        translate([0, 27, 0]) {
            cube([44, 28, 3]);
        }
    }
    
    
    for (i = [0 : 1]) {
        mirror([i, 0, 0]) {
            translate([-22, -3, 0.5]) {
                cube([10, 40, 3]);
            }
        }
    }
}*/