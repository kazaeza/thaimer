import React, { useState } from 'react';
import type { WorkoutPlan } from '../types';
import { useLanguage } from '../contexts/LanguageContext';
import { generateWorkoutFromGemini } from '../utils/gemini';
import { saveWorkout } from '../utils/storage';
import { BackIcon, SparklesIcon, SaveIcon } from './icons';
import WorkoutSetup from './WorkoutSetup';

interface WorkoutGeneratorScreenProps {
  onBack: () => void;
  onStartWorkout: (plan: WorkoutPlan) => void;
}

const WorkoutGeneratorScreen: React.FC<WorkoutGeneratorScreenProps> = ({ onBack, onStartWorkout }) => {
  const { t } = useLanguage();
  const [prompt, setPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [generatedPlan, setGeneratedPlan] = useState<WorkoutPlan | null>(null);
  const [isSaved, setIsSaved] = useState(false);

  const handleGenerate = async () => {
    if (!prompt.trim()) return;
    setIsLoading(true);
    setError(null);
    setGeneratedPlan(null);
    setIsSaved(false);

    try {
      const plan = await generateWorkoutFromGemini(prompt);
      setGeneratedPlan(plan);
    } catch (err: any) {
      setError(err.message || t('An error occurred. Please try again.'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleSave = () => {
    if (!generatedPlan) return;
    const title = window.prompt(t('Enter a title for your workout'), t('Untitled Workout'));
    if (!title) return;
    const description = window.prompt(t('Enter a short description'), prompt.substring(0, 100).trim() + '...');
    
    try {
      saveWorkout(generatedPlan, title, description || prompt.substring(0, 100).trim());
      setIsSaved(true);
    } catch (error) {
      alert(error instanceof Error ? error.message : "Could not save workout");
    }
  };

  const handleStartOver = () => {
    setPrompt('');
    setGeneratedPlan(null);
    setError(null);
    setIsSaved(false);
  };
  
  return (
    <div className="flex flex-col h-full bg-white p-6 font-sans">
      <header className="relative flex items-center justify-center mb-4">
        <button onClick={generatedPlan ? handleStartOver : onBack} className="absolute left-0 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-800" aria-label={generatedPlan ? t('Start Over') : t('Back to selection')}>
          <BackIcon className="w-8 h-8" />
        </button>
        <h1 className="text-3xl font-bold text-gray-800 text-center">{t('Workout Generator')}</h1>
      </header>

      <div className="flex-grow overflow-y-auto">
        {!generatedPlan ? (
          <div className="flex flex-col h-full">
            <textarea
              className="w-full h-40 p-3 border-2 border-gray-200 rounded-lg resize-none focus:outline-none focus:ring-2 focus:ring-orange-400"
              placeholder={t('Describe your ideal workout...')}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              disabled={isLoading}
              aria-label={t('Workout prompt')}
            />
            {error && <p className="text-red-500 mt-2 text-sm">{error}</p>}
          </div>
        ) : (
          <div>
            <p className="text-gray-500 text-center mb-2">{t('Your AI-powered workout is ready!')}</p>
            <div className="max-h-[calc(100vh-350px)] overflow-y-auto border rounded-lg p-4 bg-gray-50">
                <WorkoutSetup 
                    workoutPlan={generatedPlan}
                    onStart={() => {}}
                    onBack={() => {}}
                />
            </div>
          </div>
        )}
      </div>

      <div className="pt-4 mt-auto">
        {isLoading && (
          <div className="w-full bg-gray-300 text-gray-600 font-bold py-4 px-4 rounded-full text-2xl text-center flex items-center justify-center">
            <SparklesIcon className="w-6 h-6 mr-3 animate-pulse" />
            {t('Generating...')}
          </div>
        )}
        {!isLoading && !generatedPlan && (
          <button
            onClick={handleGenerate}
            disabled={!prompt.trim()}
            className="w-full bg-orange-500 text-white font-bold py-4 px-4 rounded-full text-2xl hover:bg-orange-600 transition-colors focus:outline-none focus:ring-2 focus:ring-orange-400 focus:ring-opacity-75 disabled:bg-gray-300 disabled:cursor-not-allowed"
          >
            {t('Generate')}
          </button>
        )}
        {!isLoading && generatedPlan && (
          <div className="space-y-3">
            <button
              onClick={() => onStartWorkout(generatedPlan)}
              className="w-full bg-orange-500 text-white font-bold py-4 px-4 rounded-full text-2xl hover:bg-orange-600 transition-colors focus:outline-none focus:ring-2 focus:ring-orange-400 focus:ring-opacity-75"
            >
              {t('Start Generated Workout')}
            </button>
            <div className="flex space-x-3">
              <button
                onClick={handleSave}
                disabled={isSaved}
                className="flex-1 bg-blue-500 text-white font-bold py-3 px-4 rounded-full text-lg hover:bg-blue-600 transition-colors flex items-center justify-center disabled:bg-green-500 disabled:cursor-not-allowed"
              >
                <SaveIcon className="w-6 h-6 mr-2" />
                {isSaved ? t('Saved') : t('Save Workout')}
              </button>
              <button
                onClick={handleStartOver}
                className="flex-1 bg-gray-200 text-gray-700 font-bold py-3 px-4 rounded-full text-lg hover:bg-gray-300 transition-colors"
              >
                {t('Start Over')}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default WorkoutGeneratorScreen;