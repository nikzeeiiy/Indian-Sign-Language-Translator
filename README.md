# ISL Translation & Voice-to-Sign Application

## Overview
The **ISL Translation & Voice-to-Sign Application** is a specialized communication tool developed to bridge the gap between the hearing-impaired community and the general public in India.  

The system focuses on **Indian Sign Language (ISL)** and provides a **dual-mode interface**:

- **Sign-to-Text Translator** – Uses computer vision to interpret hand gestures in real time.
- **Voice-to-Sign Module** – Converts spoken language into sequential sign language videos.

This enables smoother communication between people who use sign language and those who rely on speech.

---

## Features

### Sign-to-Text Translation
Real-time gesture recognition using camera input and **hand landmark tracking**.

### Distance & Mirror Invariance
Advanced mathematical logic enables gesture recognition regardless of:

- Distance from the camera
- Left-hand or right-hand usage

### Multi-Word Voice-to-Sign
Converts full spoken sentences into a sequence of **ISL videos** with smooth transitions.

### Admin Dashboard
Authorized users can:

- Record new signs
- Upload gesture videos
- Instantly train the AI model with new gestures

### Offline Database
Uses a **local SQLite database** to store:

- Hand landmark "signatures"
- Video paths

This allows **fast and offline operation**.

### Replay & Word Selection
Interactive UI allows users to:

- Replay individual words
- Replay entire sentences in sign language

---

## Technologies Used

- **Java & XML** – Core Android development  
- **MediaPipe** – Hand landmark detection and joint tracking (21 key points)  
- **SQLite** – Local database for gesture signatures and video paths  
- **Android CameraX** – High-performance camera lifecycle management  
- **Google Speech-to-Text API** – Accurate voice recognition and sentence parsing  

---

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/nikzeeiiy/Indian-Sign-Language-Translator
