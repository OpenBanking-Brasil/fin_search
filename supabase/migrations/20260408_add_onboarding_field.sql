-- Add onboarding_completed field to users table
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN DEFAULT FALSE;

-- Update existing users to have onboarding_completed as false by default
UPDATE public.users 
SET onboarding_completed = FALSE 
WHERE onboarding_completed IS NULL;