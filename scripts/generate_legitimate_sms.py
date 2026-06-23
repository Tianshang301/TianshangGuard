import csv
import random
import os

random.seed(42)

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "raw_data", "sms_spam")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "legitimate_sms_synthetic.csv")

TARGET_COUNT = 50000

BANKS = ["Chase", "Bank of America", "Wells Fargo", "Citi", "Capital One", "US Bank", "PNC", "TD Bank", "HSBC", "Barclays", "Santander", "Lloyds", "NatWest", "Monzo", "Revolut", "N26", "Ally", "Discover", "American Express", "Goldman Sachs"]
STORES = ["Amazon", "Walmart", "Target", "Costco", "Best Buy", "Home Depot", "Kroger", "Starbucks", "McDonald's", "Subway", "Nike", "Apple", "Samsung", "IKEA", "Zara", "H&M", "Sephora", "Ulta", "Petco", "GameStop"]
AIRLINES = ["Delta", "United", "American Airlines", "Southwest", "JetBlue", "British Airways", "Lufthansa", "Emirates", "Qantas", "Air Canada", "Ryanair", "EasyJet", "Air France", "KLM", "Turkish Airlines"]
CARRIERS = ["Verizon", "AT&T", "T-Mobile", "Sprint", "Vodafone", "O2", "EE", "Three", "Cricket", "Mint Mobile", "Google Fi", "Visible"]
CITIES = ["New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "London", "Manchester", "Birmingham", "Sydney", "Toronto", "Berlin", "Paris", "Tokyo", "Mumbai", "Dubai", "Singapore", "Miami", "Seattle", "Boston", "Denver", "Portland", "Austin", "Nashville", "Atlanta", "San Diego"]
NAMES = ["John", "Sarah", "Mike", "Emma", "David", "Lisa", "James", "Emily", "Robert", "Jessica", "Tom", "Alice", "Ben", "Sophie", "Chris", "Olivia", "Daniel", "Megan", "Andrew", "Rachel", "Kevin", "Laura", "Ryan", "Hannah", "Matt", "Ashley", "Jason", "Nicole", "Brian", "Stephanie"]
APPS = ["Uber", "Lyft", "DoorDash", "Grubhub", "Instacart", "Airbnb", "Netflix", "Spotify", "YouTube", "Slack", "Zoom", "Teams", "WhatsApp", "Telegram", "Signal", "Discord", "TikTok", "Instagram", "Twitter", "LinkedIn", "Reddit", "Pinterest", "Snapchat", "Venmo", "PayPal", "Cash App", "Zelle"]
HOSPITALS = ["City Hospital", "St. Mary's", "General Hospital", "Medical Center", "Health Clinic", "Care Point", "Wellness Center", "Family Practice"]
DOCTORS = ["Dr. Smith", "Dr. Johnson", "Dr. Williams", "Dr. Brown", "Dr. Jones", "Dr. Garcia", "Dr. Miller", "Dr. Davis", "Dr. Wilson", "Dr. Taylor"]
SCHOOLS = ["Harvard", "MIT", "Stanford", "Yale", "Columbia", "Princeton", "Berkeley", "Oxford", "Cambridge", "Caltech", "University of Michigan", "UCLA", "NYU", "Duke", "Cornell"]
GROCERY = ["Whole Foods", "Trader Joe's", "Safeway", "Kroger", "Publix", "Aldi", "Lidl", "Costco", "Sam's Club", "Wegmans", "H-E-B", "Meijer"]
GYMS = ["Planet Fitness", "LA Fitness", "Gold's Gym", "24 Hour Fitness", "Equinox", "Anytime Fitness", "Crunch Fitness", "YMCA", "Orangetheory", "CrossFit"]
RESTAURANTS = ["Chipotle", "Olive Garden", "Red Lobster", "Applebee's", "Chili's", "Outback", "Texas Roadhouse", "Buffalo Wild Wings", "Panera", "Shake Shack", "Five Guys", "In-N-Out", "Wendy's", "Burger King", "Taco Bell", "Pizza Hut", "Domino's", "Papa John's"]

def rand_code(length=6):
    return "".join([str(random.randint(0, 9)) for _ in range(length)])

def rand_amount(min_val=5, max_val=500):
    return f"{random.uniform(min_val, max_val):.2f}"

def rand_time():
    h = random.randint(0, 23)
    m = random.randint(0, 59)
    return f"{h:02d}:{m:02d}"

def rand_date():
    month = random.randint(1, 12)
    day = random.randint(1, 28)
    return f"{month}/{day}"

def rand_future_date():
    month = random.randint(7, 12)
    day = random.randint(1, 28)
    return f"{month}/{day}"

def rand_phone():
    return f"({random.randint(200,999)}) {random.randint(200,999)}-{random.randint(1000,9999)}"

def rand_order_id():
    return f"{random.randint(100,999)}-{random.randint(1000000,9999999)}"

def rand_tracking():
    chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return "".join(random.choices(chars, k=random.randint(10, 16)))

def generate_templates():
    templates = []

    # 1. OTP / Verification codes (12000)
    for _ in range(12000):
        bank = random.choice(BANKS + APPS + ["Google", "Microsoft", "Apple", "Facebook", "Instagram", "Twitter", "LinkedIn"])
        code = rand_code()
        templates.append(random.choice([
            f"Your {bank} verification code is {code}. Do not share this code with anyone.",
            f"{code} is your verification code. Valid for 10 minutes.",
            f"Your OTP is {code}. It expires in 5 minutes. Do not share with anyone.",
            f"Use {code} to verify your account. This code will expire in 15 minutes.",
            f"Your security code is {code}. If you did not request this, please ignore.",
            f"Verification code: {code}. Do not share this code. {bank} will never ask for it.",
            f"Your {bank} code is {code}. It will expire in {random.choice([5, 10, 15, 30])} minutes.",
            f"Enter {code} to complete your sign-in to {bank}.",
            f"Your one-time passcode is {code}. Do not share this with anyone, including {bank} staff.",
            f"Two-factor authentication code: {code}. If you didn't request this, secure your account.",
            f"Your login code is {code}. This code expires in {random.choice([5, 10])} minutes.",
            f"Use code {code} to verify your phone number with {bank}.",
            f"Your {bank} sign-in code: {code}. Never share this code.",
            f"Confirm your identity with code {code}. Valid for {random.choice([5, 10, 15])} minutes.",
            f"Your temporary access code is {code}. Use it to log in to {bank}.",
        ]))

    # 2. Bank transaction alerts (12000)
    for _ in range(12000):
        bank = random.choice(BANKS)
        amount = rand_amount()
        store = random.choice(STORES + RESTAURANTS + GROCERY)
        templates.append(random.choice([
            f"{bank}: A purchase of ${amount} was made at {store} on {rand_date()}. If this wasn't you, call us immediately.",
            f"Transaction alert: ${amount} spent at {store}. Your balance is ${rand_amount(100, 5000)}.",
            f"{bank} Alert: Payment of ${amount} to {store} was successful. Ref: {rand_code(8)}.",
            f"Your {bank} card ending in {random.randint(1000,9999)} was used for ${amount} at {store}.",
            f"Deposit of ${rand_amount(500, 5000)} received. Your available balance is ${rand_amount(1000, 10000)}. {bank}.",
            f"{bank}: Withdrawal of ${amount} at ATM {rand_code(4)}. Balance: ${rand_amount(100, 5000)}.",
            f"Direct deposit of ${rand_amount(1000, 8000)} from {random.choice(['Employer', 'Payroll', 'Company'])} has been posted to your {bank} account.",
            f"{bank}: Your credit card payment of ${rand_amount(50, 500)} has been processed successfully.",
            f"Alert: A ${amount} charge at {store} was declined. Please call {bank} at {rand_phone()}.",
            f"{bank}: Your monthly statement is ready. Log in to view your balance and transactions.",
            f"Transfer of ${rand_amount(50, 2000)} to account ending in {random.randint(1000,9999)} was successful. {bank}.",
            f"{bank}: Your savings account earned ${rand_amount(1, 50)} in interest this month.",
            f"Recurring payment of ${amount} to {store} has been processed. {bank} card ending {random.randint(1000,9999)}.",
            f"{bank}: Your check #{random.randint(1000,9999)} for ${amount} has cleared.",
            f"International transaction: ${amount} at {store}. {bank} card ending {random.randint(1000,9999)}.",
        ]))

    # 3. Delivery notifications (10000)
    for _ in range(10000):
        carrier = random.choice(["FedEx", "UPS", "USPS", "DHL", "Royal Mail", "Amazon", "Amazon Prime"])
        store = random.choice(STORES)
        templates.append(random.choice([
            f"Your {carrier} package has been delivered to your front door. Track: {rand_tracking()}.",
            f"{carrier}: Your order is out for delivery. Expected by {rand_time()} today.",
            f"Your package from {store} has shipped via {carrier}. Estimated delivery: {rand_date()}.",
            f"{carrier} Delivery Update: Your package is at the local facility and will be delivered today.",
            f"Your {carrier} tracking number is {rand_tracking()}. Track your package at {carrier.lower().replace(' ', '')}.com.",
            f"Delivered! Your {carrier} package was left at your {random.choice(['front door', 'mailbox', 'porch', 'garage'])}.",
            f"{carrier}: Your package is out for delivery. You can track it here: {carrier.lower().replace(' ', '')}.com/track",
            f"Your {store} order #{rand_order_id()} has been delivered. Enjoy!",
            f"{carrier}: Delivery attempted. We'll try again tomorrow. Or schedule a pickup at {carrier.lower().replace(' ', '')}.com.",
            f"Your package from {store} is arriving today between {rand_time()} and {rand_time()}.",
            f"{carrier}: Your package has arrived at the post office. Pick up available during business hours.",
            f"Your {store} order has been shipped! Estimated arrival: {rand_date()}. Track: {rand_tracking()}.",
            f"{carrier}: We've picked up your package. It's on its way!",
            f"Your return to {store} has been received and your refund of ${rand_amount(10, 200)} is processing.",
            f"{carrier}: Your package is delayed due to weather. New estimated delivery: {rand_date()}.",
        ]))

    # 4. Appointment reminders (8000)
    for _ in range(8000):
        doctor = random.choice(DOCTORS)
        hospital = random.choice(HOSPITALS)
        templates.append(random.choice([
            f"Reminder: Your appointment with {doctor} is tomorrow at {rand_time()}. Please arrive 10 minutes early.",
            f"Your dental appointment is scheduled for {rand_date()} at {rand_time()}. Reply CONFIRM to confirm.",
            f"{doctor}: Your appointment on {rand_date()} at {rand_time()} is confirmed.",
            f"Reminder: You have a haircut appointment at {rand_time()} today. See you soon!",
            f"Your prescription is ready for pickup at the pharmacy. Store hours: 9AM-9PM.",
            f"Appointment reminder: {doctor} at {hospital} on {rand_date()} at {rand_time()}.",
            f"Your next visit to {hospital} is on {rand_date()} at {rand_time()}. Please bring your ID and insurance card.",
            f"Reminder: Your lab work is scheduled for {rand_date()} at {rand_time()}. Fasting required.",
            f"{doctor}: Your test results are ready. Log in to the patient portal to view them.",
            f"Your therapy session with {doctor} is tomorrow at {rand_time()}. Reply CANCEL to reschedule.",
            f"Reminder: Annual checkup with {doctor} on {rand_date()}. Please complete the online forms beforehand.",
            f"Your dental cleaning is scheduled for {rand_date()} at {rand_time()}. See you at {hospital}!",
            f"{hospital}: Your flu shot appointment is confirmed for {rand_date()} at {rand_time()}.",
            f"Reminder: Follow-up appointment with {doctor} on {rand_date()} at {rand_time()}.",
            f"Your eye exam is scheduled for {rand_date()} at {rand_time()}. Please bring your current glasses.",
            f"{hospital}: Your surgery is scheduled for {rand_date()}. Pre-op instructions have been sent to your email.",
            f"Reminder: Your physical therapy session is today at {rand_time()}. Wear comfortable clothing.",
            f"Your vaccination appointment is confirmed for {rand_date()} at {rand_time()} at {hospital}.",
            f"{doctor}: Please confirm your appointment for {rand_date()} by replying YES or NO.",
            f"Reminder: Your specialist referral appointment with {doctor} is on {rand_date()}.",
        ]))

    # 5. Social / Chat messages (12000)
    for _ in range(12000):
        name = random.choice(NAMES)
        templates.append(random.choice([
            f"Hey {name}, are you free for dinner tonight?",
            f"Thanks for coming yesterday! Had a great time.",
            f"Can you pick up some milk on your way home?",
            f"Running late, be there in 15 minutes. Sorry!",
            f"Happy birthday {name}! Hope you have an amazing day!",
            f"Hey, just checking in. How are you doing?",
            f"Movie starts at 7. Want me to save you a seat?",
            f"Did you see the game last night? What a finish!",
            f"Can we reschedule our meeting to 3pm tomorrow?",
            f"Thanks for the recommendation, I'll check it out!",
            f"Are we still on for lunch tomorrow?",
            f"Just sent you the photos from the weekend trip.",
            f"Hope you feel better soon! Let me know if you need anything.",
            f"Congratulations on the new job! Well deserved.",
            f"Let me know when you're home safe.",
            f"What time does the party start?",
            f"I'm at the restaurant. Table for two by the window.",
            f"Can you believe it's already December?",
            f"Just finished my run. 5 miles in 40 minutes!",
            f"Do you want to go hiking this weekend?",
            f"Thanks for helping me move yesterday. Pizza is on me!",
            f"Just saw the funniest thing at the store.",
            f"Can you feed my cat while I'm away next week?",
            f"Guess what? I got the promotion!",
            f"Traffic is terrible. Going to be 20 minutes late.",
            f"Do you have the address for the party?",
            f"Just landed safely. See you in a few hours!",
            f"Can you believe we've been friends for 10 years?",
            f"Thanks for the birthday gift! I love it.",
            f"What are you up to this weekend?",
            f"I found a great new restaurant we should try.",
            f"Do you want to split an Uber to the airport?",
            f"Just booked my flight! Visiting in March.",
            f"Can you recommend a good plumber?",
            f"I'm thinking about getting a dog. What breed should I get?",
            f"The kids had a great time at the park today.",
            f"Do you know a good mechanic in the area?",
            f"Just finished watching that show you recommended. So good!",
            f"Can you help me with my resume?",
            f"I'm going to be a little late to the meeting.",
            f"Do you want to go to the concert next Friday?",
            f"Just made reservations for Saturday at 7pm.",
            f"Can you pick up the kids from school today?",
            f"I'm at the gym. Want to join me?",
            f"Do you have the Wi-Fi password?",
            f"Just got back from vacation. Missed you guys!",
            f"Can you believe it's already {random.choice(['Monday', 'Friday', 'the weekend'])}?",
            f"I'm thinking about redecorating my living room.",
            f"Do you want to go to the farmers market on Saturday?",
            f"Just adopted a puppy! Come meet him!",
        ]))

    # 6. Service notifications (10000)
    for _ in range(10000):
        app = random.choice(APPS)
        templates.append(random.choice([
            f"Your {app} ride will arrive in 3 minutes. Driver: {random.choice(NAMES)}.",
            f"Your {app} order has been confirmed. Estimated delivery: {rand_time()}.",
            f"Your {app} subscription has been renewed. Next billing date: {rand_date()}.",
            f"New sign-in to your {app} account from a new device. If this wasn't you, secure your account.",
            f"Your {app} report for this month is ready. Check the app for details.",
            f"Thank you for your {app} purchase. Your receipt is available in the app.",
            f"Your {app} password was changed successfully. If you didn't do this, contact support.",
            f"{app}: Your payment of ${rand_amount(5, 50)} has been processed.",
            f"Your {app} account settings have been updated. If you didn't make this change, contact us.",
            f"{app}: You have a new message from {random.choice(NAMES)}. Open the app to view it.",
            f"Your {app} trial ends on {rand_date()}. Subscribe to continue using all features.",
            f"{app}: Your data export is ready for download.",
            f"Your {app} profile has been viewed {random.randint(10, 500)} times this week.",
            f"{app}: Someone liked your post! Check it out in the app.",
            f"Your {app} backup completed successfully. {random.randint(1, 50)}GB stored.",
        ]))

    # 7. Weather alerts (4000)
    for _ in range(4000):
        city = random.choice(CITIES)
        templates.append(random.choice([
            f"Weather Alert: Rain expected in {city} tomorrow. High of {random.randint(50,85)}F.",
            f"Heat advisory for {city} area. Stay hydrated and avoid outdoor activities during peak hours.",
            f"Severe weather warning: Thunderstorms expected in {city} this evening. Stay indoors.",
            f"Good morning! {city} weather today: Sunny, high {random.randint(60,90)}F. Have a great day!",
            f"Winter storm warning: {random.randint(4,12)} inches of snow expected in {city} tonight.",
            f"Air quality alert for {city}. Sensitive groups should limit outdoor exposure.",
            f"Frost advisory for {city} tonight. Protect your plants and pipes.",
            f"UV index is very high in {city} today. Wear sunscreen and protective clothing.",
            f"Flood watch for {city} area until {rand_time()}. Avoid low-lying areas.",
            f"Wind advisory: Gusts up to {random.randint(40,70)} mph expected in {city} today.",
        ]))

    # 8. Flight / Travel updates (6000)
    for _ in range(6000):
        airline = random.choice(AIRLINES)
        flight = f"{random.choice(['AA','UA','DL','SW','BA','LH','EK','QF'])}{random.randint(100,9999)}"
        city = random.choice(CITIES)
        templates.append(random.choice([
            f"{airline} Flight {flight}: Your gate has changed to {random.choice(['A','B','C','D','E'])}{random.randint(1,50)}.",
            f"Check-in is now open for your {airline} flight to {city}. Check in via the app.",
            f"{airline}: Your flight {flight} to {city} is on time. Departure: {rand_time()}.",
            f"Your {airline} booking is confirmed. Confirmation: {rand_code(6)}. Have a safe trip!",
            f"{airline} Update: Flight {flight} has been delayed by {random.randint(30,120)} minutes.",
            f"{airline}: Your boarding pass for flight {flight} is ready. Show this at the gate.",
            f"Baggage claim: Your luggage is at carousel {random.randint(1,20)}. {airline} Flight {flight}.",
            f"{airline}: Your upgrade to business class on flight {flight} has been confirmed!",
            f"Your {airline} miles balance: {random.randint(1000,100000)} miles. Redeem for rewards.",
            f"{airline}: Flight {flight} to {city} is now boarding at gate {random.choice(['A','B','C','D'])}{random.randint(1,30)}.",
        ]))

    # 9. Carrier / Plan notifications (6000)
    for _ in range(6000):
        carrier = random.choice(CARRIERS)
        templates.append(random.choice([
            f"{carrier}: Your monthly bill of ${rand_amount(30, 150)} is due on {rand_date()}. Pay online or via the app.",
            f"Your {carrier} data usage: {random.randint(2,15)}GB of {random.randint(20,50)}GB used this cycle.",
            f"{carrier}: Your plan has been successfully renewed. Thank you for being a valued customer.",
            f"Your {carrier} roaming is now active. Enjoy your trip! Standard rates apply.",
            f"{carrier}: Your payment of ${rand_amount(30, 150)} has been received. Thank you!",
            f"Your {carrier} device upgrade is available. Check your eligibility in the app.",
            f"{carrier}: Network maintenance scheduled for {rand_date()} from {rand_time()} to {rand_time()}. Brief interruptions possible.",
            f"Your {carrier} autopay has been set up successfully. Next payment: {rand_date()}.",
            f"{carrier}: You've used {random.randint(80,100)}% of your data. Consider upgrading your plan.",
            f"Your {carrier} account is suspended due to non-payment. Please pay ${rand_amount(30, 150)} to restore service.",
        ]))

    # 10. General service / Utility (8000)
    for _ in range(8000):
        templates.append(random.choice([
            f"Your electricity bill of ${rand_amount(50, 200)} is due on {rand_date()}. Pay online to avoid late fees.",
            f"Your internet service appointment is confirmed for {rand_date()} between {rand_time()}-{rand_time()}.",
            f"Your gym membership has been renewed. Next payment: {rand_date()}.",
            f"Library reminder: Your borrowed books are due on {rand_date()}. Renew online to avoid fines.",
            f"Your car insurance policy #{rand_code(10)} has been renewed successfully.",
            f"Tax season reminder: The filing deadline is April 15. File online at irs.gov.",
            f"Your voter registration is confirmed. Polling location: {random.choice(CITIES)} Community Center.",
            f"Blood drive at the community center on {rand_date()} from {rand_time()} to {rand_time()}. Donate today!",
            f"Your passport renewal application has been received. Expected processing time: 6-8 weeks.",
            f"Your driver's license expires on {rand_future_date()}. Renew online at dmv.gov.",
            f"Home security alert: Front door opened at {rand_time()}. If this wasn't you, check your cameras.",
            f"Your smart thermostat has been set to {random.randint(68,74)}F for energy savings.",
            f"Your vehicle's oil change is due. Schedule an appointment at your local service center.",
            f"Water bill: ${rand_amount(20, 80)} due on {rand_date()}. Pay online or at any authorized location.",
            f"Your parking permit expires on {rand_future_date()}. Renew at the city website.",
            f"Trash collection reminder: Pickup is tomorrow. Place bins at the curb by 7am.",
            f"Your HOA dues of ${rand_amount(100, 500)} are due on {rand_date()}.",
            f"Your pet's vaccination is due on {rand_date()}. Schedule an appointment with your vet.",
            f"Your subscription to {random.choice(['Netflix', 'Spotify', 'Hulu', 'Disney+', 'HBO Max'])} will renew on {rand_date()} for ${rand_amount(5, 20)}.",
            f"Your annual credit report is available. View it at annualcreditreport.com.",
        ]))

    # 11. Promotional / Loyalty (6000)
    for _ in range(6000):
        store = random.choice(STORES)
        templates.append(random.choice([
            f"{store}: Flash sale! {random.randint(10,50)}% off select items today only. Shop now at {store.lower().replace(' ', '')}.com.",
            f"Your {store} rewards points balance: {random.randint(100,5000)} points. Redeem for rewards in the app.",
            f"{store}: Your order #{rand_order_id()} has been processed. Estimated pickup: {rand_date()}.",
            f"Earn double points at {store} this weekend! Show this message at checkout.",
            f"{store} exclusive: Members get early access to our summer collection. Shop now!",
            f"Your {store} coupon for {random.randint(10,30)}% off expires on {rand_date()}. Use code: {rand_code(6)}.",
            f"{store}: Thank you for your purchase! You earned {random.randint(50,500)} reward points.",
            f"Free shipping on your next {store} order! Use code FREESHIP at checkout.",
            f"{store}: Your wishlist item is back in stock! Limited quantities available.",
            f"Happy anniversary! {store} is offering you {random.randint(15,25)}% off as a thank you for {random.randint(1,10)} years of loyalty.",
        ]))

    # 12. Education / School (4000)
    for _ in range(4000):
        school = random.choice(SCHOOLS)
        templates.append(random.choice([
            f"{school}: Your application has been received. We'll notify you of our decision by {rand_date()}.",
            f"Reminder: Tuition payment of ${rand_amount(1000, 5000)} is due on {rand_date()}.",
            f"{school}: Registration for spring semester opens on {rand_date()}. Plan your courses now.",
            f"Your transcript from {school} has been sent to the requested institution.",
            f"{school}: Campus tour scheduled for {rand_date()} at {rand_time()}. Meet at the admissions office.",
            f"Financial aid notification: Your scholarship of ${rand_amount(1000, 10000)} has been awarded. {school}.",
            f"{school}: Your student ID card is ready for pickup at the campus office.",
            f"Reminder: Final exams begin on {rand_date()}. Check your schedule in the student portal.",
            f"{school}: Graduation ceremony is on {rand_date()} at {rand_time()}. RSVP by {rand_date()}.",
            f"Your library books from {school} are due on {rand_date()}. Renew online to avoid fines.",
        ]))

    # 13. Healthcare (4000)
    for _ in range(4000):
        hospital = random.choice(HOSPITALS)
        doctor = random.choice(DOCTORS)
        templates.append(random.choice([
            f"{hospital}: Your COVID-19 test result is negative. View full results in the patient portal.",
            f"Your prescription for {random.choice(['amoxicillin', 'ibuprofen', 'lisinopril', 'metformin', 'atorvastatin'])} has been filled. Pick up at the pharmacy.",
            f"{hospital}: Your insurance claim #{rand_code(10)} has been processed. Amount covered: ${rand_amount(100, 5000)}.",
            f"Reminder: Your annual flu shot is available. Walk in anytime at {hospital}.",
            f"{doctor}: Your lab results are normal. No follow-up needed at this time.",
            f"Your telehealth appointment with {doctor} is at {rand_time()}. Join via the link in your email.",
            f"{hospital}: Your medical records have been transferred as requested.",
            f"Reminder: Your mammogram is scheduled for {rand_date()} at {rand_time()}. {hospital}.",
            f"Your prescription refill request has been approved. Pick up at your pharmacy.",
            f"{hospital}: Your insurance pre-authorization for {random.choice(['MRI', 'CT scan', 'blood work', 'surgery'])} has been approved.",
        ]))

    # 14. Government / Civic (3000)
    for _ in range(3000):
        city = random.choice(CITIES)
        templates.append(random.choice([
            f"IRS: Your tax refund of ${rand_amount(500, 5000)} has been sent. Expected arrival: {rand_date()}.",
            f"DMV: Your vehicle registration renewal is due on {rand_date()}. Renew online at dmv.gov.",
            f"Social Security: Your benefit statement is available online at ssa.gov.",
            f"City of {city}: Street cleaning on your block is scheduled for {rand_date()}. Move your car by 8am.",
            f"Jury duty: You have been summoned for {rand_date()}. Report to {city} Courthouse at {rand_time()}.",
            f"USPS: A package is available for pickup at your local post office. Bring valid ID.",
            f"Federal student loan: Your payment of ${rand_amount(100, 500)} is due on {rand_date()}.",
            f"City of {city}: Your building permit #{rand_code(8)} has been approved.",
            f"State tax: Your refund of ${rand_amount(100, 1000)} has been deposited to your bank account.",
            f"Medicare: Your coverage has been updated. View your benefits at medicare.gov.",
        ]))

    # 15. Event / Entertainment (4000)
    for _ in range(4000):
        templates.append(random.choice([
            f"Your tickets for {random.choice(['concert', 'game', 'show', 'theater'])} on {rand_date()} are confirmed. Section {random.choice(['A','B','C','D'])}, Row {random.randint(1,20)}, Seat {random.randint(1,30)}.",
            f"Reminder: {random.choice(['Concert', 'Game', 'Show', 'Theater'])} starts at {rand_time()} tonight. Doors open at {rand_time()}.",
            f"Your reservation at {random.choice(RESTAURANTS)} for {random.randint(2,8)} people on {rand_date()} at {rand_time()} is confirmed.",
            f"Event update: The {random.choice(['concert', 'game', 'show'])} has been rescheduled to {rand_date()}.",
            f"Your parking pass for the {random.choice(['concert', 'game', 'event'])} on {rand_date()} is attached.",
            f"Congratulations! You won {random.randint(2,4)} tickets to {random.choice(['the movies', 'the zoo', 'the museum', 'the aquarium'])}!",
            f"Reminder: Your {random.choice(['yoga', 'pilates', 'spin', 'boxing'])} class is at {rand_time()} today.",
            f"Your membership at {random.choice(GYMS)} renews on {rand_date()} for ${rand_amount(20, 80)}/month.",
            f"Your {random.choice(['Netflix', 'Spotify', 'Hulu', 'Disney+'])} gift card of $${random.randint(10,100)} has been redeemed.",
            f"Your reservation at {random.choice(RESTAURANTS)} is in 1 hour. Party of {random.randint(2,6)}.",
        ]))

    # 16. Hard negatives: legitimate messages with phishing-like keywords (8000)
    for _ in range(8000):
        name = random.choice(NAMES)
        templates.append(random.choice([
            # Congratulations - social context
            f"Congratulations on your graduation, {name}! So proud of you!",
            f"Congratulations on the new baby! What wonderful news!",
            f"Congratulations on your promotion! You deserve it!",
            f"Congratulations on passing your exam! All that hard work paid off!",
            f"Congratulations on your wedding! Wishing you a lifetime of happiness!",
            f"Congratulations on your new home! Can't wait to visit!",
            f"Congratulations {name}! You did it! So happy for you!",
            f"Congratulations on finishing the marathon! What an achievement!",
            # Verify - legitimate context
            f"Please verify your attendance for the meeting tomorrow at {rand_time()}.",
            f"Can you verify the delivery address for your order?",
            f"Please verify your email address to complete registration.",
            f"We need to verify your appointment time. Is {rand_time()} still good?",
            f"Please verify the flight details for your upcoming trip.",
            # Click - legitimate context
            f"Click the link below to join the Zoom meeting for today's call.",
            f"Click here to view the shared document I sent you.",
            f"Click the link to confirm your subscription to our newsletter.",
            f"Click here to see the photos from last night's event.",
            # Urgent - legitimate context
            f"Urgent: The meeting has been moved to 3pm. Please confirm.",
            f"Urgent: Your package arrives today. Please be available to sign.",
            f"Urgent: The deadline for the project has been extended to Friday.",
            f"Urgent: Your prescription is ready for pickup at the pharmacy.",
            # Free - legitimate context
            f"Free coffee at the office today! Come grab one before they're gone.",
            f"Free tickets available for tonight's show. Want to come?",
            f"Free samples at the store this weekend. Want to check it out?",
            f"Free entry to the museum this Saturday. Want to go together?",
            # Account - legitimate context
            f"Your account at the library has been updated. Books due on {rand_date()}.",
            f"Your gym account has been credited with {random.randint(5,20)} free sessions.",
            f"Your account at {random.choice(STORES)} has a new reward available.",
            f"Your account at the community center is ready for activation.",
            # Prize/Reward - legitimate context
            f"You earned a reward at {random.choice(STORES)}! Check your account.",
            f"Your loyalty points at {random.choice(STORES)} are about to expire.",
            f"You've earned a free drink at {random.choice(['Starbucks', 'Dunkin', 'Peet\'s'])}!",
            f"Your reward at {random.choice(GYMS)} is ready to redeem.",
            # Suspended/Locked - legitimate context
            f"Your gym membership is temporarily suspended due to maintenance. We reopen {rand_date()}.",
            f"Your library card is locked due to overdue items. Please return them.",
            f"Your parking permit is suspended until the renewal is processed.",
            # Claim - legitimate context
            f"Your lost item has been found! Claim it at the front desk.",
            f"Your rebate for ${rand_amount(5,50)} is ready to claim. Visit our website.",
            f"Your refund of ${rand_amount(10,100)} has been processed. It will appear in 3-5 days.",
            f"Your prize from the raffle is ready to claim at the office.",
            # Verify identity - legitimate context
            f"Please bring your ID to verify your identity for the appointment.",
            f"We need to verify your identity for the account change. Call us at {rand_phone()}.",
            f"Please verify your identity at the security desk before entering.",
            # Urgent action - legitimate context
            f"Action required: Please complete your timesheet by end of day.",
            f"Action needed: Review and sign the document by {rand_date()}.",
            f"Action required: Update your emergency contact information.",
            # Limited time - legitimate context
            f"Limited time: Sign up for the workshop before {rand_date()}.",
            f"Limited spots available for the team dinner. RSVP by {rand_date()}.",
            f"Limited time offer: Register for the conference at early bird price.",
            # Click here - legitimate context
            f"Click here to RSVP for the party on {rand_date()}.",
            f"Click here to view the agenda for tomorrow's meeting.",
            f"Click here to download the app and track your order.",
            # Verify account - legitimate context
            f"Please verify your account email to activate your subscription.",
            f"Verify your account at the gym to access the new classes.",
            f"Verify your account at the library to borrow e-books.",
        ]))

    return templates

def main():
    print("Generating synthetic legitimate SMS data...")
    all_templates = generate_templates()

    # Deduplicate while preserving order
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

    output_path = os.path.join(OUTPUT_DIR, "legitimate_sms_synthetic.csv")
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label"])
        for text in templates:
            writer.writerow([text, 0])

    print(f"Saved {len(templates)} unique legitimate SMS messages")
    print(f"Saved to {output_path}")

if __name__ == "__main__":
    main()
