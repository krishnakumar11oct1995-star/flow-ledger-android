#!/usr/bin/env bash
# ==============================================================================
# Flow Ledger - Local Emulator SMS Injection Automation Script
#
# Simulates incoming bank transaction SMS broadcasts on an Android emulator or
# test device via ADB without requiring a physical SIM card or carrier network.
#
# Usage:
#   ./scripts/simulate_sms.sh [OPTION]
#   ./scripts/simulate_sms.sh --custom "VK-HDFCBK" "Rs 500 debited from a/c **4092. Bal: Rs 10000"
# ==============================================================================

set -euo pipefail

# ANSI Color Codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

APP_PACKAGE="com.flowledger.app"
RECEIVER_ACTION="android.provider.Telephony.SMS_RECEIVED"

log_info() {
    echo -e "${CYAN}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ------------------------------------------------------------------------------
# ADB Environment Validation
# ------------------------------------------------------------------------------
check_adb() {
    if ! command -v adb &> /dev/null; then
        log_error "ADB command not found. Please ensure Android SDK platform-tools is in your PATH."
        exit 1
    fi

    local devices
    devices=$(adb devices | grep -v "List of devices" | grep "device" || true)

    if [ -z "$devices" ]; then
        log_error "No active Android emulator or device found via ADB."
        log_info "Start an Android emulator in Android Studio or connect a test device with USB debugging enabled."
        exit 1
    fi
}

# ------------------------------------------------------------------------------
# Hex / PDU Encoder Helper (Python / Bash fallback)
# Generates synthetic 3GPP SMS PDUs for testing Telephony broadcast receivers
# ------------------------------------------------------------------------------
generate_pdu_hex() {
    local sender="$1"
    local message="$2"

    python3 -c "
import sys

def encode_pdu(sender_str, text):
    # Construct a minimal SMS-DELIVER 3GPP PDU
    # SMSC length: 00 (use default)
    smsc = '00'
    # First byte of SMS-DELIVER (no reply path, no more msgs, no status report)
    first_byte = '04'
    
    # Sender address encoding
    sender_clean = sender_str.replace('-', '')
    if sender_clean.isalnum() and not sender_clean.isdigit():
        # Alphanumeric Address (GSM 7-bit packed)
        sender_len = hex(len(sender_clean) * 2)[2:].zfill(2) # length in semi-octets
        type_of_addr = 'D0' # Alphanumeric, GSM 7-bit
        # Encode alphanumeric characters
        addr_hex = ''.join([hex(ord(c))[2:].zfill(2) for c in sender_clean])
        orig_addr = sender_len + type_of_addr + addr_hex
    else:
        # Numeric address
        digits = ''.join([c for c in sender_clean if c.isdigit()])
        addr_len = hex(len(digits))[2:].zfill(2)
        type_of_addr = '91' if '+' in sender_str else '81'
        if len(digits) % 2 != 0:
            digits += 'F'
        # Swap semi-octets
        swapped = ''.join([digits[i+1] + digits[i] for i in range(0, len(digits), 2)])
        orig_addr = addr_len + type_of_addr + swapped

    # Protocol ID (00 = standard SMS)
    pid = '00'
    # Data Coding Scheme (00 = GSM 7-bit default alphabet)
    dcs = '00'
    # Service Centre Timestamp (7 octets: 26 08 24 12 00 00 +00)
    scts = '62804221000000'
    
    # User Data Length (characters count)
    udl = hex(len(text))[2:].zfill(2)
    # User Data (ASCII to hex)
    ud = ''.join([hex(ord(c))[2:].zfill(2) for c in text])
    
    pdu = smsc + first_byte + orig_addr + pid + dcs + scts + udl + ud
    return pdu

print(encode_pdu('$sender', '$message'))
" 2>/dev/null || echo ""
}

# ------------------------------------------------------------------------------
# SMS Broadcast Injector
# ------------------------------------------------------------------------------
send_sms_broadcast() {
    local sender="$1"
    local message="$2"
    local description="$3"

    echo ""
    echo -e "${BOLD}══════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}Simulating Bank SMS: ${CYAN}${description}${NC}"
    echo -e "Sender : ${YELLOW}${sender}${NC}"
    echo -e "Body   : ${GREEN}${message}${NC}"
    echo -e "${BOLD}══════════════════════════════════════════════════════════════════${NC}"

    local pdu_hex
    pdu_hex=$(generate_pdu_hex "$sender" "$message")

    # Primary injection method: Direct am broadcast of SMS_RECEIVED
    if [ -n "$pdu_hex" ]; then
        log_info "Injecting raw 3GPP PDU via android.provider.Telephony.SMS_RECEIVED broadcast..."
        adb shell am broadcast \
            -a "$RECEIVER_ACTION" \
            -n "${APP_PACKAGE}/.receiver.SmsBroadcastReceiver" \
            --es format "3gpp" \
            --es pdus "$pdu_hex" > /dev/null 2>&1 || true
    fi

    # Fallback injection method: adb emu sms send (supported directly by emulator console)
    log_info "Triggering emulator modem SMS injection (adb emu sms send)..."
    local clean_num
    clean_num=$(echo "$sender" | tr -cd '0-9')
    if [ -z "$clean_num" ]; then
        clean_num="99999" # Emulator virtual short code
    fi
    adb emu sms send "$clean_num" "$message" > /dev/null 2>&1 || true

    log_success "Broadcast dispatched to ${APP_PACKAGE}. Check your home screen widget!"
}

# ------------------------------------------------------------------------------
# Test Presets
# ------------------------------------------------------------------------------
preset_hdfc_debit() {
    send_sms_broadcast \
        "VK-HDFCBK" \
        "Rs 5,400.00 debited from a/c **4092 on 24-08-26. Info: Amazon. Avl Bal: Rs 42,100.50" \
        "HDFC Bank Debit (Amazon Outflow ₹5,400)"
}

preset_hdfc_credit() {
    send_sms_broadcast \
        "AD-HDFCBK" \
        "Rs 12,000.00 credited to a/c **4092 on 24-08-26. Info: Freelance. Avl Bal: Rs 54,100.50" \
        "HDFC Bank Credit (Salary/Deposit Inflow ₹12,000)"
}

preset_icici_salary() {
    send_sms_broadcast \
        "AD-ICICIB" \
        "Acct XX102 credited with INR 85,000.00 on 23-Aug-26 by Salary. Avl Bal INR 1,20,000.00" \
        "ICICI Bank Salary Credit (Inflow ₹85,000)"
}

preset_sbi_grocery() {
    send_sms_broadcast \
        "JM-SBIBNK" \
        "Your a/c X3210 Debited Rs. 245.00 on 24Aug Tfr to JOHN. Avl Bal Rs. 14,020.56" \
        "SBI Bank Grocery Debit (Outflow ₹245)"
}

preset_axis_upi() {
    send_sms_broadcast \
        "BP-AXISBK" \
        "INR 1,200.00 sent from A/c no. XX4501 to JOHN VIA UPI. UPI Ref 423100842. Bal 5,400.00" \
        "Axis Bank UPI Sent (Outflow ₹1,200)"
}

preset_chase_usd() {
    send_sms_broadcast \
        "72166" \
        "Chase: You made a \$45.20 debit card purchase with card ending in 8912. Available balance: \$2,410.00" \
        "Chase Bank USD Purchase (Outflow $45.20)"
}

preset_otp_negative() {
    send_sms_broadcast \
        "VK-HDFCBK" \
        "482910 is your secret OTP for transaction of Rs 5,400.00 at Amazon. Do not share with anyone." \
        "NEGATIVE TEST: OTP Alert (Must be dropped in Phase 2)"
}

preset_personal_negative() {
    send_sms_broadcast \
        "+919876543210" \
        "Hey! I sent Rs 2000 to your account. Please check." \
        "NEGATIVE TEST: Personal SMS (Must be dropped in Phase 1)"
}

# ------------------------------------------------------------------------------
# Interactive CLI Menu
# ------------------------------------------------------------------------------
show_menu() {
    echo ""
    echo -e "${BOLD}${CYAN}══════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}      FLOW LEDGER - LOCAL EMULATOR SMS SIMULATION TOOL           ${NC}"
    echo -e "${BOLD}${CYAN}══════════════════════════════════════════════════════════════════${NC}"
    echo -e "  ${BOLD}1)${NC} HDFC Debit (Amazon ₹5,400 Outflow)"
    echo -e "  ${BOLD}2)${NC} HDFC Credit (Freelance ₹12,000 Inflow)"
    echo -e "  ${BOLD}3)${NC} ICICI Credit (Salary ₹85,000 Inflow)"
    echo -e "  ${BOLD}4)${NC} SBI Debit (Grocery ₹245 Outflow)"
    echo -e "  ${BOLD}5)${NC} Axis UPI Sent (Dinner ₹1,200 Outflow)"
    echo -e "  ${BOLD}6)${NC} Chase USD Debit ($45.20 Outflow)"
    echo -e "  ${BOLD}7)${NC} Negative Test: OTP Code (Verify Dropped)"
    echo -e "  ${BOLD}8)${NC} Negative Test: Personal Number (Verify Dropped)"
    echo -e "  ${BOLD}9)${NC} Custom SMS Message"
    echo -e "  ${BOLD}0)${NC} Exit"
    echo -e "${BOLD}${CYAN}──────────────────────────────────────────────────────────────────${NC}"
    read -rp "Select an option [0-9]: " choice

    case "$choice" in
        1) preset_hdfc_debit ;;
        2) preset_hdfc_credit ;;
        3) preset_icici_salary ;;
        4) preset_sbi_grocery ;;
        5) preset_axis_upi ;;
        6) preset_chase_usd ;;
        7) preset_otp_negative ;;
        8) preset_personal_negative ;;
        9)
            echo ""
            read -rp "Enter Sender Header (e.g. VK-HDFCBK): " custom_sender
            read -rp "Enter SMS Body text: " custom_body
            send_sms_broadcast "$custom_sender" "$custom_body" "Custom Transaction SMS"
            ;;
        0) exit 0 ;;
        *) log_error "Invalid option."; exit 1 ;;
    esac
}

# ------------------------------------------------------------------------------
# Entrypoint
# ------------------------------------------------------------------------------
check_adb

if [ $# -eq 0 ]; then
    show_menu
else
    case "$1" in
        --hdfc-debit) preset_hdfc_debit ;;
        --hdfc-credit) preset_hdfc_credit ;;
        --icici) preset_icici_salary ;;
        --sbi) preset_sbi_grocery ;;
        --axis) preset_axis_upi ;;
        --chase) preset_chase_usd ;;
        --otp) preset_otp_negative ;;
        --personal) preset_personal_negative ;;
        --custom)
            if [ $# -lt 3 ]; then
                log_error "Usage: $0 --custom <SENDER> <MESSAGE>"
                exit 1
            fi
            send_sms_broadcast "$2" "$3" "Custom SMS"
            ;;
        --help|-h)
            echo "Usage: $0 [OPTION]"
            echo "Options:"
            echo "  --hdfc-debit       Simulate HDFC Amazon debit (₹5,400)"
            echo "  --hdfc-credit      Simulate HDFC deposit (₹12,000)"
            echo "  --icici            Simulate ICICI Salary credit (₹85,000)"
            echo "  --sbi              Simulate SBI grocery debit (₹245)"
            echo "  --axis             Simulate Axis UPI payment (₹1,200)"
            echo "  --chase            Simulate Chase USD card debit ($45.20)"
            echo "  --otp              Simulate OTP message (Should be dropped)"
            echo "  --personal         Simulate personal mobile text (Should be dropped)"
            echo "  --custom <S> <M>   Inject custom sender and message"
            ;;
        *)
            log_error "Unknown option: $1. Use --help for usage."
            exit 1
            ;;
    esac
fi
