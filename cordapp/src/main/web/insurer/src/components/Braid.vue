
<template>
  <span>Braid Proxy</span>
</template>

<script>
  import {Proxy} from 'braid-client';

  export default {
  name: "Braid",
  created() {
    this.onOpen = function() {
      console.log('braid connected', this.proxy);
    };
    this.onClose = function() {
      console.log('braid closed');
    };
    this.onError = function(err) {
      console.error('connection error', err);
    }
  },
  mounted() {
    const path = window.location.protocol + '//' + window.location.host + '/api/';
    console.log('connect to', path);
    this.proxy = new Proxy( { url: path }, this.onOpen, this.onClose, this.onError);
  },
  methods: {
    getNodeName() {

    }
  }
}
</script>
