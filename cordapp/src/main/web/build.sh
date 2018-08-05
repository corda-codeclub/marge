#!/usr/bin/env bash

pushd bank
npm install
npm run build
popd

pushd hospital
npm install
npm run build
popd

pushd insurer
npm install
npm run build
popd

