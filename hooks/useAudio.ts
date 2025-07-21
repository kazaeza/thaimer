import { useRef, useCallback } from 'react';

export const useAudio = () => {
  const audioCtx = useRef<AudioContext | null>(null);
  const isInitialized = useRef(false);

  const init = useCallback(() => {
    if (isInitialized.current) return;
    try {
      audioCtx.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      isInitialized.current = true;
    } catch (e) {
      console.error("Web Audio API is not supported in this browser");
    }
  }, []);

  const playBeep = useCallback((freq: number, duration: number, volume: number) => {
    if (!audioCtx.current) return;
    const oscillator = audioCtx.current.createOscillator();
    const gainNode = audioCtx.current.createGain();

    gainNode.connect(audioCtx.current.destination);
    oscillator.connect(gainNode);
    
    oscillator.type = 'sine';
    oscillator.frequency.value = freq;
    gainNode.gain.setValueAtTime(volume, audioCtx.current.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.0001, audioCtx.current.currentTime + duration);

    oscillator.start(audioCtx.current.currentTime);
    oscillator.stop(audioCtx.current.currentTime + duration);
  }, []);

  const playStartSound = useCallback(() => {
    playBeep(440, 0.1, 0.5);
  }, [playBeep]);

  const playUrgentSound = useCallback(() => {
    playBeep(880, 0.1, 0.6);
  }, [playBeep]);

  return { init, playStartSound, playUrgentSound };
};