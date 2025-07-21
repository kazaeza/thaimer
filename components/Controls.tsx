import React from 'react';
import { PlayIcon, PauseIcon, NextIcon, PrevIcon, ListIcon, BackIcon } from './icons';
import { useLanguage } from '../contexts/LanguageContext';

interface ControlsProps {
  isPaused: boolean;
  onPlayPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onToggleSummary: () => void;
  onBack: () => void;
}

const Controls: React.FC<ControlsProps> = ({ isPaused, onPlayPause, onNext, onPrev, onToggleSummary, onBack }) => {
  const { t } = useLanguage();
  return (
    <div className="w-full flex items-center justify-around p-4">
      <button onClick={onBack} className="text-gray-400 hover:text-gray-600 transition-colors" aria-label={t('Back to workout list')}>
        <BackIcon className="w-8 h-8" />
      </button>
      <button onClick={onPrev} className="text-gray-600 hover:text-black transition-colors" aria-label={t('Previous exercise')}>
        <PrevIcon className="w-10 h-10" />
      </button>
      <button
        onClick={onPlayPause}
        className="w-20 h-20 bg-orange-500 text-white rounded-full flex items-center justify-center shadow-lg hover:bg-orange-600 transition-colors transform hover:scale-105"
        aria-label={t(isPaused ? "Play" : "Pause")}
      >
        {isPaused ? <PlayIcon className="w-10 h-10 ml-1" /> : <PauseIcon className="w-10 h-10" />}
      </button>
      <button onClick={onNext} className="text-gray-600 hover:text-black transition-colors" aria-label={t('Next exercise')}>
        <NextIcon className="w-10 h-10" />
      </button>
      <button onClick={onToggleSummary} className="text-gray-400 hover:text-gray-600 transition-colors" aria-label={t('Show workout summary')}>
        <ListIcon className="w-7 h-7" />
      </button>
    </div>
  );
};

export default Controls;