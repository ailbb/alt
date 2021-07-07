package com.ailbb.alt.exception;

import com.ailbb.ajj.$;
import com.ailbb.ajj.entity.$Result;
import com.ailbb.ajj.exception.$Exception;

/*
 * Created by Wz on 8/23/2018.
 */
public class $FtpException {
    public static class $LoginErrorException extends $Exception {
        public $LoginErrorException() {
            super();
        }
        public $LoginErrorException(String msg){ super(msg); }
        public $LoginErrorException($Result rs){ super($.join(rs.getMessage())); }
    }

    public static class $CommondErrorException extends $Exception {
        public $CommondErrorException() {
            super();
        }
        public $CommondErrorException(String msg){ super(msg); }
        public $CommondErrorException($Result rs){ super($.join(rs.getMessage())); }
    }
}
