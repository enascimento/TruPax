package coderslagoon.trupax.lib.io.filesystem.udf;

import java.util.Calendar;
import java.util.TimeZone;

import coderslagoon.baselib.util.BinUtils;

public class Timestamp {
    public final static int LENGTH = 12;

    // time zones...
    public final static int COORDINATED_UNIVERSAL_TIME = 0;
    public final static int LOCAL_TIME = 1;
    public final static int CUSTOM_TIME = 2;

    public final static int NO_OFFSET = -2047;

    public int timeZone;
    public int offsetMinutesUTC;
    public int year;                    // 1..9999
    public int month;                   // 1..12
    public int day;                     // 1..31
    public int hour;                    // 0..23
    public int minute;                  // 0..59
    public int second;                  // 0..59/0..60 if CUSTOM_TIME
    public int centiseconds;            // 0..99
    public int hundredsOfMicroseconds;  // 0..99
    public int microseconds;            // 0..99

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Timestamp) {
            Timestamp ts = (Timestamp)obj;

            return this.timeZone               == ts.timeZone               &&
                   this.offsetMinutesUTC       == ts.offsetMinutesUTC       &&
                   this.year                   == ts.year                   &&
                   this.month                  == ts.month                  &&
                   this.day                    == ts.day                    &&
                   this.hour                   == ts.hour                   &&
                   this.minute                 == ts.minute                 &&
                   this.second                 == ts.second                 &&
                   this.centiseconds           == ts.centiseconds           &&
                   this.hundredsOfMicroseconds == ts.hundredsOfMicroseconds &&
                   this.microseconds           == ts.microseconds;
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (null == this.hashCode) {
            this.hashCode =
                this.timeZone               +
                this.offsetMinutesUTC       +
                this.year                   +
                this.month                  +
                this.day                    +
                this.hour                   +
                this.minute                 +
                this.second                 +
                this.centiseconds           +
                this.hundredsOfMicroseconds +
                this.microseconds;
        }
        return this.hashCode;
    }
    Integer hashCode;

    final static int int12ToInt32(int v) {
        v &= 0x0fff;
        return (v & 0x0fff) | (0 == (v & 0x800) ? 0 : 0xfffff000);
    }

    public static Timestamp parse(byte[] buf, int ofs) {
        Timestamp result = new Timestamp();

        int tatz                      = BinUtils.readInt16LE(buf, ofs);
        result.timeZone               = (tatz >>> 12) & 0x0f;
        result.offsetMinutesUTC       = int12ToInt32(tatz);             ofs += 2;
        result.year                   = BinUtils.readInt16LE(buf, ofs); ofs += 2;
        result.month                  = buf[ofs++];
        result.day                    = buf[ofs++];
        result.hour                   = buf[ofs++];
        result.minute                 = buf[ofs++];
        result.second                 = buf[ofs++];
        result.centiseconds           = buf[ofs++];
        result.hundredsOfMicroseconds = buf[ofs++];
        result.microseconds           = buf[ofs];

        return result;
    }

    public static Timestamp fromCalendar(Calendar cal) {
        Timestamp result = new Timestamp();
        int millis = cal.get(Calendar.MILLISECOND);

        result.year                   = cal.get(Calendar.YEAR);
        result.month                  = cal.get(Calendar.MONTH) + 1;
        result.day                    = cal.get(Calendar.DAY_OF_MONTH);
        result.hour                   = cal.get(Calendar.HOUR_OF_DAY);
        result.minute                 = cal.get(Calendar.MINUTE);
        result.second                 = cal.get(Calendar.SECOND);
        result.centiseconds           =  millis / 10;
        result.hundredsOfMicroseconds = (millis % 10) * 10;
        result.microseconds           = 0;
        result.timeZone               = LOCAL_TIME;
        result.offsetMinutesUTC       = (cal.get(Calendar.ZONE_OFFSET) +
                                         cal.get(Calendar.DST_OFFSET)) / 60000;
        return result;
    }

    final static TimeZone TZ_GMT = TimeZone.getTimeZone("GMT");
    final static TimeZone TZ_LOC = TimeZone.getDefault();

    public Calendar toCalendar() {
        boolean noOfs = NO_OFFSET == this.offsetMinutesUTC;

        Calendar cal = Calendar.getInstance(noOfs ? TZ_LOC : TZ_GMT);

        cal.set(Calendar.YEAR        , this.year);
        cal.set(Calendar.MONTH       , this.month - 1);
        cal.set(Calendar.DAY_OF_MONTH, this.day);
        cal.set(Calendar.HOUR_OF_DAY , this.hour);
        cal.set(Calendar.MINUTE      , this.minute);
        cal.set(Calendar.SECOND      , this.second);
        cal.set(Calendar.MILLISECOND , this.centiseconds           * 10 +
                                       this.hundredsOfMicroseconds / 10);
        if (noOfs) {
            return cal;
        }

        Calendar result = Calendar.getInstance(TZ_GMT);

        result.setTimeInMillis(cal.getTimeInMillis() -
                               this.offsetMinutesUTC * 60000L);
        return result;
    }

    public void write(byte[] buf, int ofs) {
        int tatz = (this.timeZone << 12) | (this.offsetMinutesUTC & 0x0fff);

        BinUtils.writeInt16LE((short)tatz     , buf, ofs); ofs += 2;
        BinUtils.writeInt16LE((short)this.year, buf, ofs); ofs += 2;

        buf[ofs++] = (byte)this.month;
        buf[ofs++] = (byte)this.day;
        buf[ofs++] = (byte)this.hour;
        buf[ofs++] = (byte)this.minute;
        buf[ofs++] = (byte)this.second;
        buf[ofs++] = (byte)this.centiseconds;
        buf[ofs++] = (byte)this.hundredsOfMicroseconds;
        buf[ofs  ] = (byte)this.microseconds;
    }

    public String toString() {
        return String.format("TZ=%d,ofs=%d,tm=%d/%d/%d-%02d:%02d:%02d.%02d%02d%02d",
                this.timeZone,
                this.offsetMinutesUTC,
                this.year,
                this.month,
                this.day,
                this.hour,
                this.minute,
                this.second,
                this.centiseconds,
                this.hundredsOfMicroseconds,
                this.microseconds);
    }
}
