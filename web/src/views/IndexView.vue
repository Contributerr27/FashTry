<script setup>
import { userState } from '../lib/userStore.js';
import HomeView from './HomeView.vue';
import LandingView from './LandingView.vue';
</script>

<template>
  <LandingView v-if="!userState.loggedIn" />
  <HomeView v-else />
</template>
