import csv
import random
import os

random.seed(42)

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "raw_data", "sms_spam")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "phishing_sms_synthetic.csv")

TARGET_COUNT = 30000

BANKS = ["Chase", "Bank of America", "Wells Fargo", "Citi", "Capital One", "US Bank", "PNC", "TD Bank", "HSBC", "Barclays", "Santander", "Lloyds", "NatWest", "Monzo", "Revolut", "N26", "Ally", "Discover", "American Express", "Goldman Sachs"]
SERVICES = ["Amazon", "PayPal", "Apple", "Google", "Microsoft", "Netflix", "Spotify", "Uber", "Lyft", "DoorDash", "Instacart", "Airbnb", "Facebook", "Instagram", "Twitter", "LinkedIn", "TikTok", "Snapchat", "WhatsApp", "Telegram"]
STORES = ["Walmart", "Target", "Costco", "Best Buy", "Home Depot", "Kroger", "Starbucks", "McDonald's", "Subway", "Nike", "IKEA", "Zara", "H&M", "Sephora", "GameStop"]

def rand_code(length=6):
    return "".join([str(random.randint(0, 9)) for _ in range(length)])

def rand_amount(min_val=100, max_val=10000):
    return f"{random.randint(min_val, max_val)}"

def rand_url():
    domains = ["bit.ly", "tinyurl.com", "goo.gl", "t.co", "rb.gy", "short.link", "ow.ly", "is.gd", "buff.ly", "tiny.cc"]
    return f"https://{random.choice(domains)}/{random.randint(10000, 99999)}"

def generate_templates():
    templates = []

    # 1. Account locked/suspended patterns (5000)
    for _ in range(5000):
        service = random.choice(BANKS + SERVICES)
        templates.append(random.choice([
            f"Your {service} account has been locked. Click here to verify your identity: {rand_url()}",
            f"ALERT: Your {service} account has been suspended. Verify now: {rand_url()}",
            f"{service} Security: Your account is locked due to unusual activity. Click to unlock: {rand_url()}",
            f"Your {service} account has been temporarily suspended. Please verify your identity immediately: {rand_url()}",
            f"Important: Your {service} account is locked. Click here to restore access: {rand_url()}",
            f"{service}: Your account has been locked for security reasons. Verify now: {rand_url()}",
            f"Your {service} account access is restricted. Click here to verify: {rand_url()}",
            f"Security Alert: Your {service} account is suspended. Verify your identity: {rand_url()}",
            f"{service}: Unusual sign-in activity detected. Your account is locked. Verify: {rand_url()}",
            f"Your {service} account has been locked. Click here to confirm your identity: {rand_url()}",
        ]))

    # 2. Congratulations/Prize patterns (5000)
    for _ in range(5000):
        store = random.choice(STORES + SERVICES)
        templates.append(random.choice([
            f"Congratulations! You have won a ${rand_amount()} gift card from {store}! Claim here: {rand_url()}",
            f"WINNER! You've been selected to receive a ${rand_amount()} {store} gift card. Claim now: {rand_url()}",
            f"Congratulations! You won ${rand_amount()} in our weekly draw! Claim your prize: {rand_url()}",
            f"You've won! Claim your ${rand_amount()} {store} reward now: {rand_url()}",
            f"CONGRATULATIONS! You are our lucky winner! Claim your ${rand_amount()} prize: {rand_url()}",
            f"Congratulations on winning our ${rand_amount()} sweepstakes! Claim here: {rand_url()}",
            f"You've been selected! Claim your free ${rand_amount()} {store} gift card: {rand_url()}",
            f"WINNER ALERT: You've won ${rand_amount()}! Claim your prize before it expires: {rand_url()}",
            f"Congratulations! Your phone number won ${rand_amount()} in our lottery! Claim: {rand_url()}",
            f"You've won a brand new iPhone! Claim your prize now: {rand_url()}",
        ]))

    # 3. Free gift/reward patterns (5000)
    for _ in range(5000):
        store = random.choice(STORES + SERVICES)
        templates.append(random.choice([
            f"FREE {store} gift card worth ${rand_amount()}! Limited time offer. Claim: {rand_url()}",
            f"You have a free ${rand_amount()} {store} reward waiting! Claim now: {rand_url()}",
            f"Exclusive: Free {store} gift card for you! Get yours: {rand_url()}",
            f"Your free ${rand_amount()} reward from {store} is ready! Claim: {rand_url()}",
            f"FREE GIFT: {store} is giving away ${rand_amount()} gift cards! Claim yours: {rand_url()}",
            f"You qualified for a free ${rand_amount()} {store} card! Claim here: {rand_url()}",
            f"Special offer: Free {store} gift card worth ${rand_amount()}! Claim now: {rand_url()}",
            f"Your ${rand_amount()} free reward is waiting! Claim at {rand_url()}",
            f"FREE: {store} ${rand_amount()} gift card for loyal customers! Claim: {rand_url()}",
            f"You earned a free ${rand_amount()} {store} reward! Claim before it expires: {rand_url()}",
        ]))

    # 4. Urgent action patterns (5000)
    for _ in range(5000):
        service = random.choice(BANKS + SERVICES)
        templates.append(random.choice([
            f"URGENT: Your {service} account needs verification. Act now: {rand_url()}",
            f"URGENT ACTION REQUIRED: Your {service} account will be suspended. Verify: {rand_url()}",
            f"IMMEDIATE ACTION: Your {service} account is at risk. Verify now: {rand_url()}",
            f"URGENT: Suspicious activity on your {service} account. Verify immediately: {rand_url()}",
            f"URGENT: Your {service} payment failed. Update your information: {rand_url()}",
            f"URGENT: Your {service} subscription is expiring. Renew now: {rand_url()}",
            f"URGENT: Your {service} account has been compromised. Secure now: {rand_url()}",
            f"URGENT: Your {service} order cannot be processed. Update payment: {rand_url()}",
            f"URGENT: Your {service} account requires immediate verification: {rand_url()}",
            f"URGENT: Your {service} account will be deleted. Verify now: {rand_url()}",
        ]))

    # 5. Verify identity patterns (5000)
    for _ in range(5000):
        service = random.choice(BANKS + SERVICES)
        templates.append(random.choice([
            f"Please verify your {service} account to avoid suspension: {rand_url()}",
            f"{service}: We need to verify your identity. Click here: {rand_url()}",
            f"Your {service} account requires verification. Verify now: {rand_url()}",
            f"{service} Security: Please verify your account immediately: {rand_url()}",
            f"Verify your {service} account to continue using our services: {rand_url()}",
            f"{service}: Your account verification is pending. Complete now: {rand_url()}",
            f"Your {service} account needs identity verification. Click to verify: {rand_url()}",
            f"{service}: Account verification required. Verify here: {rand_url()}",
            f"Please confirm your {service} account details: {rand_url()}",
            f"{service}: Your account verification is incomplete. Complete now: {rand_url()}",
        ]))

    # 6. Tax/Government scam patterns (3000)
    for _ in range(3000):
        templates.append(random.choice([
            f"IRS: You have a pending tax refund of ${rand_amount()}. Claim now: {rand_url()}",
            f"Tax Alert: Your tax return has been flagged. Verify immediately: {rand_url()}",
            f"IRS: Your tax refund of ${rand_amount()} is ready. Claim: {rand_url()}",
            f"Government Alert: You have an unpaid fine. Pay now to avoid arrest: {rand_url()}",
            f"Social Security: Your benefits will be suspended. Verify now: {rand_url()}",
            f"IRS: You owe ${rand_amount()} in back taxes. Pay immediately: {rand_url()}",
            f"Tax Refund: You are eligible for a ${rand_amount()} refund. Claim: {rand_url()}",
            f"Government Notice: Your tax filing is under review. Verify: {rand_url()}",
            f"IRS Alert: Your tax return has an error. Correct now: {rand_url()}",
            f"Social Security Administration: Your account is compromised. Verify: {rand_url()}",
        ]))

    # 7. Delivery/Package scam patterns (3000)
    for _ in range(3000):
        templates.append(random.choice([
            f"Your package cannot be delivered. Update your address: {rand_url()}",
            f"UPS: Your package is held at customs. Pay fee to release: {rand_url()}",
            f"FedEx: Your delivery failed. Reschedule: {rand_url()}",
            f"USPS: Your package is pending. Confirm your address: {rand_url()}",
            f"DHL: Your shipment is on hold. Pay customs fee: {rand_url()}",
            f"Amazon: Your order cannot be shipped. Update payment: {rand_url()}",
            f"Your package is waiting for delivery confirmation: {rand_url()}",
            f"Delivery Alert: Your package requires a signature. Confirm: {rand_url()}",
            f"Your package has been returned. Reschedule delivery: {rand_url()}",
            f"Shipping Alert: Your package is delayed. Track here: {rand_url()}",
        ]))

    # 8. Romance/Social engineering patterns (2000)
    for _ in range(2000):
        templates.append(random.choice([
            f"Hey, I saw your profile and I think we should chat! Check out my photos: {rand_url()}",
            f"Someone has a crush on you! Find out who: {rand_url()}",
            f"You have a secret admirer! See their message: {rand_url()}",
            f"I'm stuck overseas and need help. Can you send money? Details: {rand_url()}",
            f"Your ex is looking for you. See their message: {rand_url()}",
            f"You won't believe what people are saying about you! See here: {rand_url()}",
            f"I found your lost photos online! See them here: {rand_url()}",
            f"Someone shared a photo of you! View it: {rand_url()}",
            f"Your friend needs your help urgently. See their message: {rand_url()}",
            f"I have important information about your family. Contact me: {rand_url()}",
        ]))

    # 9. Investment/Crypto scam patterns (2000)
    for _ in range(2000):
        templates.append(random.choice([
            f"Make ${rand_amount()} per day with this simple trick! Learn more: {rand_url()}",
            f"Bitcoin is going to the moon! Invest now and double your money: {rand_url()}",
            f"This stock is about to explode! Get in before it's too late: {rand_url()}",
            f"I made ${rand_amount()} in one week trading crypto! You can too: {rand_url()}",
            f"Exclusive investment opportunity! Guaranteed ${rand_amount()}% returns: {rand_url()}",
            f"Join our trading group and make ${rand_amount()} daily! Sign up: {rand_url()}",
            f"Crypto alert: Buy now before the price doubles! Details: {rand_url()}",
            f"Passive income opportunity! Make ${rand_amount()} while you sleep: {rand_url()}",
            f"Financial freedom is just one click away! Start now: {rand_url()}",
            f"Don't miss this! ${rand_amount()} profit in just 24 hours: {rand_url()}",
        ]))

    # 10. NO-URL phishing patterns: classic text-only scams (8000)
    for _ in range(8000):
        service = random.choice(BANKS + SERVICES)
        templates.append(random.choice([
            # Account locked - no URL
            f"Your {service} account has been locked. Call us immediately to verify.",
            f"ALERT: Your {service} account has been suspended. Call now to restore access.",
            f"{service} Security: Your account is locked. Call this number immediately.",
            f"Your {service} account has been temporarily locked. Call to verify your identity.",
            # Congratulations - no URL
            f"Congratulations! You have won a ${rand_amount()} gift card! Call to claim.",
            f"WINNER! You've been selected to receive ${rand_amount()}. Call now to claim.",
            f"Congratulations! You won our weekly draw! Call this number to claim your prize.",
            f"You've won! Call now to claim your ${rand_amount()} reward before it expires.",
            # Free gift - no URL
            f"FREE gift card worth ${rand_amount()}! Call now to claim yours.",
            f"You have a free ${rand_amount()} reward waiting! Call to claim.",
            f"Exclusive: Free ${rand_amount()} gift card for you! Call now.",
            f"Your free ${rand_amount()} reward is ready! Call to claim before it expires.",
            # Urgent - no URL
            f"URGENT: Your {service} account needs verification. Call now.",
            f"URGENT ACTION REQUIRED: Your {service} account will be suspended. Call immediately.",
            f"IMMEDIATE ACTION: Your {service} account is at risk. Call now.",
            f"URGENT: Suspicious activity on your {service} account. Call to verify.",
            # Verify identity - no URL
            f"Please verify your {service} account to avoid suspension. Call now.",
            f"{service}: We need to verify your identity. Call this number.",
            f"Your {service} account requires verification. Call to verify.",
            f"{service} Security: Please verify your account immediately. Call now.",
            # Tax/Government - no URL
            f"IRS: You have a pending tax refund. Call now to claim.",
            f"Tax Alert: Your tax return has been flagged. Call immediately.",
            f"IRS: Your tax refund is ready. Call to claim.",
            f"Government Alert: You have an unpaid fine. Call to pay now.",
            # Delivery - no URL
            f"Your package cannot be delivered. Call to update your address.",
            f"UPS: Your package is held at customs. Call to pay fee.",
            f"FedEx: Your delivery failed. Call to reschedule.",
            f"Your package is pending. Call to confirm delivery.",
            # Romance/Social - no URL
            f"Hey, I saw your profile and I think we should chat! Call me.",
            f"Someone has a crush on you! Call to find out who.",
            f"You have a secret admirer! Call to see their message.",
            # Investment - no URL
            f"Make ${rand_amount()} per day! Call for details.",
            f"Double your money in one week! Call now.",
            f"Exclusive investment opportunity! Call for details.",
            f"Financial freedom is one call away! Call now.",
            # Prize/Lottery - no URL
            f"You've been selected for our ${rand_amount()} prize! Call to claim.",
            f"Your phone number won ${rand_amount()} in our lottery! Call to claim.",
            f"You've won a brand new iPhone! Call to claim your prize.",
            f"Claim your ${rand_amount()} prize before it expires! Call now.",
            # Account security - no URL
            f"Your {service} password has been compromised. Call to reset.",
            f"Security Alert: Your {service} account has been accessed from a new device. Call now.",
            f"Your {service} account has been hacked. Call immediately to secure it.",
            f"Your {service} account shows unusual activity. Call to verify.",
            # Payment/Billing - no URL
            f"Your {service} payment has failed. Call to update your payment method.",
            f"Your {service} subscription has expired. Call to renew.",
            f"Your {service} account is past due. Call to make a payment.",
            f"Your {service} billing information needs to be updated. Call now.",
            # General scam - no URL
            f"This is your final warning. Call now or your account will be closed.",
            f"Your account will be terminated in 24 hours. Call immediately.",
            f"Legal action will be taken against you. Call now to resolve.",
            f"You have been reported for fraud. Call to clear your name.",
        ]))

    # 11. AMBIGUOUS phishing patterns: "Click here", "Claim now" without clear context (6000)
    for _ in range(6000):
        service = random.choice(BANKS + SERVICES)
        store = random.choice(STORES + SERVICES)
        templates.append(random.choice([
            # "Click here" patterns
            f"Your {service} account has been locked. Click here to verify your identity immediately.",
            f"ALERT: Your {service} account is suspended. Click here to restore access.",
            f"Your {service} account needs verification. Click here to confirm.",
            f"Security Alert: Your {service} account is compromised. Click here to secure it.",
            f"Your {service} account shows unusual activity. Click here to verify.",
            f"Important: Your {service} account requires verification. Click here now.",
            # "Claim now" patterns
            f"Congratulations! You have won a ${rand_amount()} gift card from {store}! Claim now!",
            f"WINNER! You've been selected to receive ${rand_amount()}! Claim your prize now!",
            f"You have a ${rand_amount()} reward waiting! Claim it before it expires!",
            f"Your ${rand_amount()} gift card from {store} is ready! Claim now!",
            f"Congratulations! You won ${rand_amount()} in our weekly draw! Claim now!",
            f"You've been selected! Claim your ${rand_amount()} {store} gift card now!",
            # "Text to" patterns
            f"Free entry in a weekly competition! Text WIN to {random.randint(10000, 99999)} to claim your prize.",
            f"Text {random.randint(10000, 99999)} to claim your free ${rand_amount()} gift card!",
            f"Text CLAIM to {random.randint(10000, 99999)} to receive your ${rand_amount()} reward!",
            f"Text YES to {random.randint(10000, 99999)} to claim your prize!",
            # "Act now" patterns
            f"Act now! Your {service} account will be suspended if you don't verify immediately.",
            f"Act now! Your {store} reward expires today! Claim before it's too late!",
            f"Act now! Your ${rand_amount()} prize is waiting! Claim before it expires!",
            f"Act now! Your {service} account shows suspicious activity. Verify now!",
            # "Limited time" patterns
            f"Limited time: Your ${rand_amount()} {store} gift card expires today! Claim now!",
            f"Limited time offer: Free ${rand_amount()} reward! Claim before it's gone!",
            f"Limited time: Your {service} account needs verification. Act now!",
            f"Limited time: Claim your ${rand_amount()} prize before it expires!",
            # "Verify your account" patterns
            f"Please verify your {service} account to avoid suspension. Act now!",
            f"Your {service} account requires immediate verification. Verify now!",
            f"{service}: Your account verification is pending. Complete it now!",
            f"Verify your {service} account to continue using our services. Act now!",
        ]))

    return templates

def main():
    print("Generating synthetic phishing SMS data...")
    all_templates = generate_templates()

    # Deduplicate
    seen = set()
    unique_templates = []
    for t in all_templates:
        if t not in seen:
            seen.add(t)
            unique_templates.append(t)

    print(f"Generated {len(all_templates)} total, {len(unique_templates)} unique")

    # Shuffle and take target count
    random.shuffle(unique_templates)
    templates = unique_templates[:TARGET_COUNT]

    output_path = os.path.join(OUTPUT_DIR, "phishing_sms_synthetic.csv")
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label"])
        for text in templates:
            writer.writerow([text, 1])

    print(f"Saved {len(templates)} unique phishing SMS messages")
    print(f"Saved to {output_path}")

if __name__ == "__main__":
    main()
