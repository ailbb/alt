package com.ailbb.alt.exception;

import com.ailbb.ajj.exception.$Exception;

/**
 * Created by Wz on 8/23/2018.
 */
public class $LinuxException {
    public static class $ConnectErrorException extends $Exception {
        public $ConnectErrorException() {
            super();
        }
        public $ConnectErrorException(String msg){ super(msg); }
    }

    public static class $LoginErrorException extends $Exception {
        public $LoginErrorException() {
            super();
        }
        public $LoginErrorException(String msg){ super(msg); }
    }

    public static class $CommondErrorException extends $Exception {
        public $CommondErrorException() {
            super();
        }
        public $CommondErrorException(String msg){ super(msg); }
    }
}
