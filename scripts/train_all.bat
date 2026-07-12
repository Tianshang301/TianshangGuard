@echo off
cd /d "F:\Projects\Project11\scripts"

echo ====== URL Model Training (started at %date% %time%) ====== > "training_url.log"
python train_phishing_model.py --fresh --mode url >> "training_url.log" 2>&1
echo ====== URL Model Finished at %date% %time% (exit=%errorlevel%) ====== >> "training_url.log"

echo ====== English SMS Model Training (started at %date% %time%) ====== > "training_english.log"
python train_phishing_model.py --fresh --mode english >> "training_english.log" 2>&1
echo ====== English SMS Finished at %date% %time% (exit=%errorlevel%) ====== >> "training_english.log"

echo ====== Chinese SMS Model Training (started at %date% %time%) ====== > "training_sms.log"
python train_phishing_model.py --fresh --mode sms >> "training_sms.log" 2>&1
echo ====== Chinese SMS Finished at %date% %time% (exit=%errorlevel%) ====== >> "training_sms.log"

echo ====== Chinese Text Model Training (started at %date% %time%) ====== > "training_chinese.log"
python train_phishing_model.py --fresh --mode chinese >> "training_chinese.log" 2>&1
echo ====== Chinese Text Finished at %date% %time% (exit=%errorlevel%) ====== >> "training_chinese.log"

echo ALL TRAINING COMPLETE at %date% %time%
