#!/usr/bin/perl

use strict;
use warnings;

for my $row (1..32) {
    my $directory = 'layout';
    die "Unable to create $directory\n" unless(-e $directory or mkdir $directory);
    for my $col (1..32) {
        my $file = "$directory/widget_tile_${col}x${row}.xml";

        open(my $fh, '>', $file) or die "Cannot create $file: $!";

        print $fh <<XML;
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="\@+id/widget_layout_${row}x${col}"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_columnSpan="$col"
    android:layout_rowSpan="$row"
    android:layout_columnWeight="$col"
    android:layout_rowWeight="$row"
    android:layout_gravity="fill"
    android:orientation="vertical">
    <include layout="\@layout/widget_tile" />
</FrameLayout>
XML

        close($fh);
    }
}

print "Generated 1024 XML layout files.\n";
